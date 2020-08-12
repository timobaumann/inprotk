/**   
*           The HMM-Based Speech Synthesis System (HTS)             
*                       HTS Working Group                           
*                                                                   
*                  Department of Computer Science                   
*                  Nagoya Institute of Technology                   
*                               and                                 
*   Interdisciplinary Graduate School of Science and Engineering    
*                  Tokyo Institute of Technology                    
*                                                                   
*                Portions Copyright (c) 2001-2006                       
*                       All Rights Reserved.
*                         
*              Portions Copyright 2000-2007 DFKI GmbH.
*                      All Rights Reserved.     
*                      
*           Portions Copyright 2011 Universität Hamburg
*                                                                   
*  Permission is hereby granted, free of charge, to use and         
*  distribute this software and its documentation without           
*  restriction, including without limitation the rights to use,     
*  copy, modify, merge, publish, distribute, sublicense, and/or     
*  sell copies of this work, and to permit persons to whom this     
*  work is furnished to do so, subject to the following conditions: 
*                                                                   
*    1. The source code must retain the above copyright notice,     
*       this list of conditions and the following disclaimer.       
*                                                                   
*    2. Any modifications to the source code must be clearly        
*       marked as such.                                             
*                                                                   
*    3. Redistributions in binary form must reproduce the above     
*       copyright notice, this list of conditions and the           
*       following disclaimer in the documentation and/or other      
*       materials provided with the distribution.  Otherwise, one   
*       must contact the HTS working group.                         
*                                                                   
*  NAGOYA INSTITUTE OF TECHNOLOGY, TOKYO INSTITUTE OF TECHNOLOGY,   
*  HTS WORKING GROUP, AND THE CONTRIBUTORS TO THIS WORK DISCLAIM    
*  ALL WARRANTIES WITH REGARD TO THIS SOFTWARE, INCLUDING ALL       
*  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS, IN NO EVENT   
*  SHALL NAGOYA INSTITUTE OF TECHNOLOGY, TOKYO INSTITUTE OF         
*  TECHNOLOGY, HTS WORKING GROUP, NOR THE CONTRIBUTORS BE LIABLE    
*  FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY        
*  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,  
*  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTUOUS   
*  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR          
*  PERFORMANCE OF THIS SOFTWARE.                                    
*                                                                   
*/

package inpro.synthesis.hts;

import marytts.htsengine.HMMData;
import marytts.htsengine.HTSPStream;
import marytts.htsengine.HTSParameterGeneration;
import marytts.htsengine.HTSVocoder;
import marytts.util.data.BaseDoubleDataSource;
import marytts.util.data.DoubleDataSource;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * a vocoding thread that that can return immediately and from which the 
 * synthesized audio can be read while synthesis is still in progress;
 * this class heavily relies on and borrows from HTSVocoder
 * @author timo
 */
public class VocodingAudioStream extends BaseDoubleDataSource implements Runnable {

	private static double MAX_AMPLITUDE_START_VALUE = 32768;
	
    private final Random rand = new Random(HTSVocoder.SEED);
    private final HMMData htsData;
    private final double alpha, beta, gamma;
    /** Gamma=-1/stage : if stage=0 then Gamma=0 */
    private final int stage; 
    private final boolean use_log_gain;
    /** frame shift */
    private final int fprd;        
    /** used in excitation generation */
    private double p1 = -1.0;     // set to -1; will be changed in the first frame 
    /** used in excitation generation */
    private double pc = 0.0;       
    /** used in the MLSA/MGLSA filter */
    private double C[];            
    /** used in the MLSA/MGLSA filter */
    private double CC[];           
    /** used in the MLSA/MGLSA filter */
    private double CINC[];         
    /** used in the MLSA/MGLSA filter */
    private double D1[];           
    /** used in mlsadf2 */
    private final int pt2;                            
    /** used in mlsadf2 */
    private final int pt3[] = new int[HTSVocoder.PADEORDER + 1];
    /** the parameter streams for synthesis, all in one object */
    FullPStream fullPStream;
    
    /** mcepPst.getOrder() */
    private final int mcepOrder; 
    private final boolean mixedExcitation;
    private final boolean fourierMagnitudes;
    
    /** queue for the output samples */
    private final ArrayBlockingQueue<Double> output;

    // TODO: think about how gain scaling can be incrementalized
    private double maxAmplitude = MAX_AMPLITUDE_START_VALUE; //32768;
    
    boolean doneVocoding = false;
    
    boolean firstDelivery = true;
    
    public static double gain = 0.3;
    
    Logger logger = Logger.getLogger("Vocoding Audio Stream");
    
    /** for backward compatibility with Mary */
    public VocodingAudioStream(HTSParameterGeneration pdf2par, HMMData htsData, boolean immediateReturn) { // NO_UCD (unused code): for backward compatibility with Mary
        this(new HTSFullPStream(pdf2par), htsData, immediateReturn);
    }
    
    /** 
     * Vocode based on a FullPStream.
     * initialization happens in this constructor, actual vocoding is implemented in {@link #run()} further down.
     * @param immediateReturn whether to vocode in a separate thread and return immediately, allowing for concurrent processing   
     */
    public VocodingAudioStream(FullPStream pstream, HMMData htsData, boolean immediateReturn) {
        this.htsData = htsData;
        this.fullPStream = pstream;
        alpha = htsData.getAlpha();
        beta  = htsData.getBeta();
        gamma = htsData.getGamma();
        stage = htsData.getStage();
        use_log_gain = htsData.getUseLogGain();
        fprd  = htsData.getFperiod();
        mixedExcitation = htsData.getUseMixExc();
        fourierMagnitudes = htsData.getUseFourierMag();

        mcepOrder = fullPStream.getMcepParSize();
        /* initialize MLSA/MGLSA filter buffers */
        int mcepBufferSize = mcepOrder + 1;
        C    = new double[mcepBufferSize];
        CC   = new double[mcepBufferSize];
        CINC = new double[mcepBufferSize];
        if (stage == 0){  /* for MCP */
            /* mcep_order=74 and pd=PADEORDER=5 (if no HTS_EMBEDDED is used) */
            int vector_size = ((mcepOrder * HTSPStream.NUM - 1) * (3 + HTSVocoder.PADEORDER) + 5 * HTSVocoder.PADEORDER + 6) - (3 * mcepBufferSize);
            D1   = new double[vector_size];
            pt2 = (2 * (HTSVocoder.PADEORDER + 1)) + (HTSVocoder.PADEORDER * (mcepBufferSize + 1));
            for(int i=HTSVocoder.PADEORDER; i>=1; i--)
                pt3[i] = (2 * (HTSVocoder.PADEORDER + 1)) + ((i - 1) * (mcepBufferSize + 1));
        } else { /* for LSP */
            pt2 = 0;
            int vector_size = (mcepOrder * HTSPStream.NUM * (stage + 3)) - (3 * mcepBufferSize );
            D1 = new double[vector_size];   
        }
        this.dataLength = pstream.getMaxT() * fprd;
        output = new ArrayBlockingQueue<Double>((int) dataLength); // we allow to store the complete output in the queue
        // start processing
        if (immediateReturn) {
            new Thread(this, "VocodingAudioStream").start();
        } else {
            run();
        }
	}
    
    @Override
    public String toString() {
    	return "vocoding audio stream fed by " + fullPStream.toString();
    }
    
	/** incrementally vocode and generate output */
    @Override
	public void run() {
        try {
        /** initial processing of the vocoder, before any output is being generated */
        double [] magPulse = null;         /* pulse generated from Fourier magnitudes */
        
        double hp[] = null;   /* pulse shaping filter, it is initialised once it is known orderM */  
        double hn[] = null;   /* noise shaping filter, it is initialised once it is known orderM */  

        int numM = htsData.getNumFilters();
        int orderM = htsData.getOrderFilters();

        double[][] h = null; // mix filters when usig mixed excitation
        double[] xpulseSignal = null;
        double[] xnoiseSignal = null;
        
        if (mixedExcitation && htsData.getPdfStrStream() != null ) {  
          xpulseSignal = new double[orderM];
          xnoiseSignal = new double[orderM];
          /* initialise xp_sig and xn_sig */ // -> automatically initialized to 0.0
          h = htsData.getMixFilters();
          hp = new double[orderM];  
          hn = new double[orderM]; 
                
          //Check if the number of filters is equal to the order of strpst 
          //i.e. the number of filters is equal to the number of generated strengths per frame.
          if(htsData.getNumFilters() != fullPStream.getStrParSize()) {
            logger.debug("htsMLSAVocoder: error num mix-excitation filters =" + numM + " in configuration file is different from generated str order=" + fullPStream.getStrParSize());
            throw new RuntimeException("htsMLSAVocoder: error num mix-excitation filters = " + numM + " in configuration file is different from generated str order=" + fullPStream.getStrParSize());
          }
          logger.info("HMM speech generation with mixed-excitation.");
        } else
          logger.info("HMM speech generation without mixed-excitation.");  
        
        if( fourierMagnitudes && htsData.getPdfMagStream() != null)
          logger.info("Pulse generated with Fourier Magnitudes.");
        else
          logger.info("Pulse generated as a unit pulse.");
        
        if (beta != 0.0)
          logger.info("Postfiltering applied with beta=" + beta);
        else
          logger.info("No postfiltering applied.");

        double f0Std = htsData.getF0Std();
        double f0Shift = htsData.getF0Mean();
        double f0MeanOri = 0.0;

        /* _______________________Synthesize speech waveforms_____________________ */
        /* generate Nperiod samples per mcepframe */
        int magSample = 1;
        int magPulseSize = 0;
        fullPStream.setNextFrame(0);
        //int frameCounter = 0;
        while (fullPStream.hasNextFrame()) {
          double inc;
          FullPFeatureFrame frame = fullPStream.getNextFrame();
          if (frame == null) 
        	  break; // abort if no frame is left (for whatever reason)

          //System.err.println("frame " + frameCounter + ": " + frame.toString());
          //frameCounter++;

          /* feature vector for a particular frame */
          /* get current feature vector mcp */ 
          double[] mc = frame.getMcepParVec();
     
          /* f0 modification through the MARY audio effects */
          double f0 = 0.0;
          if(frame.isVoiced()){
            f0 = f0Std * Math.exp(frame.getlf0Par()) + (1 - f0Std) * f0MeanOri + f0Shift;
            f0 = Math.max(0.0, f0);
          }
           
          /* if mixed excitation get shaping filters for this frame 
           * the strength of pulse, is taken from the predicted value, which can be maximum 1.0, 
           * and the strength of noise is the rest -> 1.0 - strPulse */
          if (mixedExcitation) {
            double[] localStrParVec = frame.getStrParVec();
            Arrays.fill(hp, 0.0);
            Arrays.fill(hn, 0.0);
            for(int j=0; j<orderM; j++) {
              for(int i=0; i<numM; i++) {         
                hp[j] += localStrParVec[i] * h[i][j];
                hn[j] += (1 - localStrParVec[i]) * h[i][j];
              }
            }
          }
          

          /* f0 -> pitch , in original code here it is used p, so f0=p in the c code */
          if(f0 != 0.0)
             f0 = htsData.getRate() / f0;
          // f0 now holds fundamental period instead of frequency  
          
          /* p1 is initialised to -1, so this will be done just for the first frame */
          if( p1 < 0 ) {  
            p1 = f0;           
            pc = p1;   
            /* for LSP */
            if (stage != 0){
              C[0] = (use_log_gain) ? HTSVocoder.LZERO : HTSVocoder.ZERO;
              double PI_m = Math.PI / mcepOrder;
              for (int i = 0; i < mcepOrder; i++)  
                C[i] = i * PI_m;
              /* LSP -> MGC */
              lsp2mgc(C, C);
              HTSVocoder.mc2b(C, C, (mcepOrder-1), alpha);
              HTSVocoder.gnorm(C, C, (mcepOrder-1), gamma);
              for(int i=1; i<mcepOrder; i++)
                C[i] *= gamma;   
            }
          }
          
          if (stage == 0){         
            /* postfiltering, this is done if beta>0.0 */
            HTSVocoder.postfilter_mgc(mc, (mcepOrder-1), alpha, beta);
            /* mc2b: transform mel-cepstrum to MLSA digital filter coefficients */   
            HTSVocoder.mc2b(mc, CC, (mcepOrder-1), alpha);
            for (int i = 0; i < mcepOrder; i++)
              CINC[i] = (CC[i] - C[i]) * HTSVocoder.IPERIOD / fprd;
          } else {
            lsp2mgc(mc, CC);
            HTSVocoder.mc2b(CC, CC, (mcepOrder-1), alpha);
            HTSVocoder.gnorm(CC, CC, (mcepOrder-1), gamma);
            for (int i = 1; i < mcepOrder; i++)
              CC[i] *= gamma;
            for (int i = 0; i < mcepOrder; i++)
              CINC[i] = (CC[i] - C[i]) * HTSVocoder.IPERIOD / fprd;
          } 
          
          /* p=f0 in c code!!! */
          if( p1 != 0.0 && f0 != 0.0 ) {
            inc = (f0 - p1) * HTSVocoder.IPERIOD / fprd;
          } else {
            inc = 0.0;
            pc = f0;
            p1 = 0.0; 
          }
                
          /* Here i need to generate both xp:pulse and xn:noise signals separately  */ 
          // gauss = false; /* Mixed excitation works better with normal noise */
        
          /* Generate fperiod samples per feature vector, normally 80 samples per frame */
          //p1=0.0;
          double x;
          double xp=0.0,xn=0.0;  /* samples for pulse and for noise and the filtered ones */
          for (int j = fprd - 1, i = (HTSVocoder.IPERIOD + 1) / 2; j >= 0; j--) {          
            if (p1 == 0.0) {
              x = (rand.nextBoolean()) ? 1.0 : -1.0;
              if (mixedExcitation) {
                xn = x;
                xp = 0.0;
              }
            } else {
                if ((pc += 1.0) >= p1) {
                  if (fourierMagnitudes) {
                    magPulse = HTSVocoder.genPulseFromFourierMag(frame.getMagParVec(), p1);
                    magSample = 0;
                    magPulseSize = magPulse.length;
                    x = magPulse[magSample];
                    magSample++;
                  } else
                	x = Math.sqrt(p1);  
                  
                  pc -= p1;
                } else {
                  if (fourierMagnitudes) {
                    if (magSample >= magPulseSize ) { 
                      x = 0.0;
                    }
                    else
                      x = magPulse[magSample];                
                    magSample++;
                  } else
                     x = 0.0;                 
                }
                if (mixedExcitation) {
                  xp = x;
                  xn = (rand.nextBoolean()) ? 1.0 : -1.0;
                }
            } 
            
            /* apply the shaping filters to the pulse and noise samples */
            /* i need memory of at least for M samples in both signals */
            if (mixedExcitation) {
              double fxp = 0.0;
              double fxn = 0.0;
              for (int k = orderM - 1; k > 0; k--) {
                fxp += hp[k] * xpulseSignal[k];
                fxn += hn[k] * xnoiseSignal[k];
                xpulseSignal[k] = xpulseSignal[k-1];
                xnoiseSignal[k] = xnoiseSignal[k-1];
              }
              fxp += hp[0] * xp;
              fxn += hn[0] * xn;
              xpulseSignal[0] = xp;
              xnoiseSignal[0] = xn;
              /* x is a pulse noise excitation and mix is mixed excitation */
              double mix = fxp+fxn;
              /* comment this line if no mixed excitation, just pulse and noise */
              x = mix;   /* excitation sample */
            }
             
            if (stage == 0) {
              if (x != 0.0)
                x *= Math.exp(C[0]);
              x = HTSVocoder.mlsadf(x, C, mcepOrder, alpha, D1, pt2, pt3);
            } else {
               x *= C[0];
               x = HTSVocoder.mglsadf(x, C, (mcepOrder-1), alpha, stage, D1);
            }
            output.put(Double.valueOf(x));
            if ((--i) == 0 ) {
              p1 += inc;
              for (int k = 0; k < mcepOrder; k++) {
                C[k] += CINC[k];  
              }
              i = HTSVocoder.IPERIOD;
            }
          } /* for each sample in a period fprd */
          p1 = f0;
       
          /* move elements in c */
          System.arraycopy(CC, 0, C, 0, mcepOrder);
        } /* for each mcep frame */
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        doneVocoding = true;
    }
    
    /** lsp2mgc: transform LSP to MGC.  lsp=C[0..m]  mgc=C[0..m] */
    private final void lsp2mgc(double lsp[], double mgc[]) {
      /* lsp2lpc */
      HTSVocoder.lsp2lpc(lsp, mgc, mcepOrder - 1);  /* lsp starts in 1!  lsp[1..m] --> mgc[0..m] */
      if (use_log_gain)
        mgc[0] = Math.exp(lsp[0]);
      else
        mgc[0] = lsp[0];
      /* mgc2mgc*/
      HTSVocoder.ignorm(mgc, mgc, mcepOrder - 1, gamma);  
      for (int i = mcepOrder - 1; i > 0; i--) 
        mgc[i] *= -stage;    
      HTSVocoder.mgc2mgc(mgc, mcepOrder - 1, alpha, gamma, mgc, mcepOrder - 1, alpha, gamma);  /* input and output is in mgc=C */
    }

    @Override
    public int available() {
        return output.size();
    }

    @Override
    public long getDataLength() {
    	if (!doneVocoding)
    		return DoubleDataSource.NOT_SPECIFIED; // not specified
    	else
    		return dataLength;
    }

    @Override
    public boolean hasMoreData() {
        return !doneVocoding;
    }

    @Override
    public int getData(double[] target, int targetPos, int length) {
    	if (available() == 0) {
    		try { Thread.sleep(2); // take it easy, buddy.
			} catch (InterruptedException e) { e.printStackTrace();	}
    	}
        int outputAmount = Math.min(available(), length);
        try {
            for (int i = 0; i < outputAmount; i++) {
                Double d;
                d = output.take();
                target[i + targetPos] = scale(d.doubleValue());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (firstDelivery) {
        	Logger.getLogger("speedlogger").info("first samples");
        	firstDelivery = false;
        }
        return outputAmount;
    }

    int samples = 0;
    
    private final double scale(double d) {
    	samples++;
    	if (Math.abs(d) > maxAmplitude) {
    		System.err.println("max amplitude: " + d + " in sample: " + samples);
//    		maxAmplitude = Math.abs(d);
    	}
//    	else if (maxAmplitude > MAX_AMPLITUDE_START_VALUE) {
//   			maxAmplitude -= 0.05 * maxAmplitude;
//    	}
        return gain * d / maxAmplitude;
    } 
    
    public int getSamplingRate() {
    	return htsData.getRate();
    }
    
}