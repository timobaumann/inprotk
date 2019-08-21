package inpro.synthesis.hts;

import marytts.htsengine.*;
import marytts.htsengine.HMMData.FeatureType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/** 
 * Inpro-clone of MaryTTS's HTSParameterGeneration (but working incrementally)
 * 
 * this class performs HSMM observation optimization for all supported types of 
 * parameter streams (same as in MaryTTS).  
 */
public class PHTSParameterGeneration {

	private boolean[] voiced;
    
    private final HMMData htsData; 
    
    public PHTSParameterGeneration(HMMData htsData) {
    	this.htsData = htsData;
	}
    
    public FullPStream buildFullPStreamFor(List<HTSModel> hmms) {
        // find out what types of streams we're dealing with:
        Set<FeatureType> features = htsData.getFeatureSet();
        // original code does not deal with durations, so I explicitly remove DUR to make sure that it's never there; this might in fact not be necessary
        features.remove(FeatureType.DUR);
        //assert !features.contains(FeatureType.LF0);
    	return buildFullPStreamFor(hmms, features);
    }

    /** 
     * build a parameter stream for some given HMMs
     * there can only ever be one call to buildFullPStreamFor() per object because 
     */
	private synchronized FullPStream buildFullPStreamFor(List<HTSModel> hmms, Set<FeatureType> features) {
		HashMap<FeatureType, HTSPStream> pStreamMap = new HashMap<FeatureType, HTSPStream>();
		for (FeatureType type : features) { // these could be submitted concurrently to an ExecutorService
			if (type == FeatureType.LF0) {
				pStreamMap.put(type, calculateLF0Stream(hmms));
			} else
				pStreamMap.put(type, calculateNormalStream(hmms, type));
		}
		return new HTSFullPStream(pStreamMap.get(FeatureType.MGC),
								  pStreamMap.get(FeatureType.STR),
								  pStreamMap.get(FeatureType.MAG),
								  pStreamMap.get(FeatureType.LF0),
								  voiced);
	}
	
	/** fill in data into PStream and run optimization */
	private HTSPStream calculateNormalStream(List<HTSModel> hmms, FeatureType type) {
		assert type != FeatureType.LF0;
		CartTreeSet ms = htsData.getCartTreeSet();
		// initialize pStream
		int maxIterationsGV = (type == FeatureType.MGC) ? htsData.getMaxMgcGvIter() : htsData.getMaxLf0GvIter();
		int length = lengthOfEmissions(hmms, type);
		HTSPStream pStream = null;
		try {
			pStream = new HTSPStream(ms.getVsize(type), length, type, maxIterationsGV);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// fill in data into pStream
		int uttFrame = 0; // count all frames
		for (HTSModel hmm : hmms) {
			for (int state = 0; state < ms.getNumStates(); state++) { // number of states is uniform for all HMMs
				for (int frame = 0; frame < hmm.getDur(state); frame++) {
					/* copy pdfs for types */
					pStream.setMseq(uttFrame, hmm.getMean(type, state));
		            pStream.setVseq(uttFrame, hmm.getVariance(type, state));
					uttFrame++;
		}}}
		// ensure that we set variances to inf at the last frame (similarly to v/uv boundaries) to avoid downwards slopes
		for (int k = 1; k < ms.getVsize(type); k++) {
			pStream.setIvseq(uttFrame - 1, k, 0.0);
		}
		return optimizeStream(pStream, type);
	}
	
	/** 
	 * like calculateNormalStream for FeatureType.LF0 which requires some additional voiced/voiceless handling
	 * !! also sets this.voiced[] to appropriate values
	 */ 
	private HTSPStream calculateLF0Stream(List<HTSModel> hmms) {
		CartTreeSet ms = htsData.getCartTreeSet();
		// initialize pStream
		int maxIterationsGV = htsData.getMaxLf0GvIter();
		int totalLength = 0;
		int voicedLength = 0;
		for (HTSModel hmm : hmms) {
			voicedLength += hmm.getNumVoiced();
			totalLength += hmm.getTotalDur();
		}
		HTSPStream pStream = null;
		try {
			pStream = new HTSPStream(ms.getLf0Stream(), voicedLength, FeatureType.LF0, maxIterationsGV);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// figure out voicing
		int uttFrame = 0; // count all frames
		int lf0Frame = 0; // count all voiced frames
		voiced = new boolean[totalLength]; // automatically initialized to false
		boolean prevVoicing = false; // records whether the last state was voiced 
		for (HTSModel hmm : hmms) {
			for (int state = 0; state < ms.getNumStates(); state++) {
				// fill in data into pStream
				if (hmm.getVoiced(state)) {
					Arrays.fill(voiced, uttFrame, uttFrame + hmm.getDur(state), true);
					// handle boundaries between voiced/voiceless states
					boolean boundary = !prevVoicing; // if the last state was voiceless and this one is voiced, we have a boundary  
					for (int frame = 0; frame < hmm.getDur(state); frame++) {
						// copy pdfs for types 
						pStream.setMseq(lf0Frame, hmm.getMean(FeatureType.LF0, state));
						if (boundary) {// the variances for dynamic features are set to inf on v/uv boundary
							pStream.setIvseq(lf0Frame, 0, HTSParameterGeneration.finv(hmm.getLf0Variance(state, 0)));
							for (int k = 1; k < ms.getLf0Stream(); k++)
								pStream.setIvseq(lf0Frame, k, 0.0);
							boundary = false; // clear flag: we've set the boundary
						} else {
							pStream.setVseq(lf0Frame, hmm.getVariance(FeatureType.LF0, state));
						}
						lf0Frame++;
					}
				}
				uttFrame += hmm.getDur(state);
				prevVoicing = hmm.getVoiced(state);
			}
		}
		// ensure that we set variances to inf at the last frame (similarly to v/uv boundaries) to avoid downwards slopes
		if (lf0Frame > 0) for (int k = 1; k < ms.getLf0Stream(); k++) {
			pStream.setIvseq(lf0Frame - 1, k, 0.0);
		}
		return optimizeStream(pStream, FeatureType.LF0);
	}
	
	private HTSPStream optimizeStream(HTSPStream pStream, FeatureType type) {
		boolean useGV = useGVperType(type);
		setGVMeanVar(pStream, type);
		pStream.mlpg(htsData, useGV);
		return pStream;
	}

	private boolean useGVperType(FeatureType type) {
		switch (type) {
		case STR: return htsData.getUseGV() && htsData.getPdfStrGVStream() != null;
		case MAG: return htsData.getUseGV() && htsData.getPdfMagGVStream() != null;
		//TODO: find out why GV for MCP doesn't work and make it work 
		case MGC: return false; //htsData.getUseGV(); // for some reason, MCP GV does not work incrementally!
		default: return htsData.getUseGV(); // LF0 and DUR
		}
//		return false;
	}

	private void setGVMeanVar(HTSPStream pStream, FeatureType type) {
		GVModelSet gvms = htsData.getGVModelSet();
		switch (type) {
		case STR: 
			pStream.setGvMeanVar(gvms.getGVmeanStr(), gvms.getGVcovInvStr());
			break;
		case MAG:
			pStream.setGvMeanVar(gvms.getGVmeanMag(), gvms.getGVcovInvMag());
			break;
		case MGC:
			pStream.setGvMeanVar(gvms.getGVmeanMgc(), gvms.getGVcovInvMgc());
			break;
		case LF0:
			pStream.setGvMeanVar(gvms.getGVmeanLf0(), gvms.getGVcovInvLf0());
			break; 
		default: throw new RuntimeException("don't call me with " + type.toString()); 
		}
	}

	private static int lengthOfEmissions(List<HTSModel> hmms, FeatureType type) {
		int length = 0;
		for (HTSModel hmm : hmms) {
			if (type == FeatureType.LF0) {
				// TODO: make sure that numVoiced is set already (by whom?), otherwise set it myself
				length += hmm.getNumVoiced();
			} else {
				length += hmm.getTotalDur();
			}
		}
		return length;
	}  

}
