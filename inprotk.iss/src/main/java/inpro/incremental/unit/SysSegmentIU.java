package inpro.incremental.unit;

import inpro.annotation.Label;
import inpro.config.SynthesisConfig;
import inpro.synthesis.hts.FullPFeatureFrame;
import inpro.synthesis.hts.FullPStream;
import inpro.synthesis.hts.PHTSParameterGeneration;
import inpro.synthesis.hts.VocodingFramePostProcessor;
import marytts.features.FeatureVector;
import marytts.htsengine.HMMData;
import marytts.htsengine.HTSModel;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SysSegmentIU extends SegmentIU {
	
	private static Logger logger = Logger.getLogger(SysSegmentIU.class);
	
	Label originalLabel;
	/** the speech synthesis HMM models to be used for this segment */
	public HMMData hmmdata;

	/** the feature vector as it was derived from surrounding symbolic information (ideally from within the IU network, de-facto by MaryTTS) */
	public FeatureVector fv;
	public HTSModel legacyHTSmodel;
	/** the HMM states to be used for the segment (as determined from the feature vector) */
	HTSModel htsModel;
	/** the synthesis parameters that result from HMM optimization */
	public List<FullPFeatureFrame> hmmSynthesisFeatures;
	
	/** post-processing options: */
	public double pitchShiftInCent = 0.0;
	public double pitchExcitationFactor = 1;
	private VocodingFramePostProcessor vocodingFramePostProcessor;

	/** the state of delivery that this unit is in */
	IU.Progress progress = IU.Progress.UPCOMING;
	/** more detailed status during Progress.ONGOING: the number of frames that this segment has already been going on */
	int realizedDurationInSynFrames = 0;
	
	/** used to mark that a continuation will follow, even though no fSLL exists yet */
	boolean awaitContinuation; 
	
	public SysSegmentIU(Label l, HTSModel htsModel, FeatureVector fv, HMMData hmmdata, List<FullPFeatureFrame> featureFrames) {
		super(l);
		this.fv = fv;
		this.hmmdata = hmmdata;
		this.hmmSynthesisFeatures = featureFrames;
		if (htsModel != null)
			this.setHTSModel(htsModel);
	}

	public SysSegmentIU(Label l) {
		this(l, null, null, null, null);
	}
	
	@Override
	public StringBuilder toMbrolaLine() {
		StringBuilder sb = l.toMbrola();
/* TODO: regenerate pitch marks from hmmSynthesisFrames
 * this is not really urgent though, as toMbrolaLine is only used for debugging anyway, not in production code */
		sb.append("\n");
		return sb;
	}
	
	/**	the duration of this segment in multiples of 5 ms */
	public int durationInSynFrames() {
		return (int) Math.round(duration() * FullPStream.FRAMES_PER_SECOND / Double.valueOf(System.getProperty("inpro.tts.tempoScaling", "1.0")));
	}
	
	public double originalDuration() {
		if (originalLabel != null)
			return originalLabel.getDuration();
		else
			return duration();
	}
	
	/* * * * * * * * * * * * * * * * * * * * * * * * */
	/* replacement/recontextualisation of synthesis  */
	/* * * * * * * * * * * * * * * * * * * * * * * * */
	
	/** copy all data necessary for synthesis -- i.e. the htsModel and pitchmarks */
	public void copySynData(SysSegmentIU newSeg) {
		assert payloadEquals(newSeg);
		this.l = newSeg.l;
		this.hmmdata = newSeg.hmmdata;
		this.fv = newSeg.fv;
		
		setHTSModel(newSeg.getHTSModel());
		this.legacyHTSmodel = newSeg.legacyHTSmodel;
		this.htsModel = newSeg.htsModel;
		
		this.hmmSynthesisFeatures = newSeg.hmmSynthesisFeatures;
	}

	/* * * * * * * * * * * * * * * * * * * * * * * */
	/*   interruption/continuation of synthesis    */
	/* * * * * * * * * * * * * * * * * * * * * * * */
	
	/**
	 * tell this segment that it will be followed by more input later on
	 * @return whether a continuation is possible (i.e., the segment hasn't been completed yet
	 */
	public synchronized boolean setAwaitContinuation(boolean b) {
		awaitContinuation = b;
		notifyAll();
		return !isCompleted();
	}
	
	/** awaits the awaitContinuation field to be cleared */
	private synchronized void awaitContinuation() {
		while (awaitContinuation) {
			try { wait(); } catch (InterruptedException e) {}
		}		
	}
	
	/** adds fSLL, and allows continuation of synthesis */
	@Override
	public synchronized void addNextSameLevelLink(IU iu) {
		super.addNextSameLevelLink(iu);
		awaitContinuation = false;
		notifyAll();
	}
	
	/* * * * * * * * * * * * * * * * * * * * * * * * */
	/*   HMM state handling (from Feature Vector)    */
	/* * * * * * * * * * * * * * * * * * * * * * * * */
	
	HTSModel getHTSModel() {
		if (htsModel != null) 
			return htsModel;
		htsModel = generateHTSModel();
		if (htsModel != null) {
		assert this.legacyHTSmodel != null;
		// for evaluation of various incremental FV computation strategies:
		//	HTSModelComparator.compare(htsModel, legacyHTSmodel);
		//	IncrementalCARTTest.same(htsModel, legacyHTSmodel);
			return htsModel;
		} else 
			return legacyHTSmodel;
	}
	
	public void setHTSModel(HTSModel hmm) {
		legacyHTSmodel = hmm;
	}

	HTSModel generateHTSModel() {
//		FeatureVector fv = ComputeHMMFeatureVector.featuresForSegmentIU(this);
//		double prevErr = getSameLevelLink() != null && getSameLevelLink() instanceof SysSegmentIU ? ((SysSegmentIU) getSameLevelLink()).getHTSModel().getDurError() : 0f;
//		HTSModel htsModel = hmmdata.getCartTreeSet().generateHTSModel(hmmdata, hmmdata.getFeatureDefinition(), fv, prevErr);
		//possibly throw away htsModel's f0-values if you don't like it:
		// htsModel.setMaryXmlF0("");
//		return htsModel;
		return legacyHTSmodel;
	}
	
	/** helper, append HMM if possible, return emission length */
	private static int appendSllHtsModel(List<HTSModel> hmms, IU sll) {
		int length = 0;
		if (sll != null && sll instanceof SysSegmentIU) {
			HTSModel hmm = ((SysSegmentIU) sll).getHTSModel(); // document if you feel like you need to change this line (in particular, please document the reason, why this might better be legacyHTSmodel
			hmms.add(hmm);
			length = hmm.getTotalDur();
		}
		return length;
	}
	
	/* * * * * * * * * * * * * * * * * * * * */
	/*   vocoding features                   */
	/* * * * * * * * * * * * * * * * * * * * */
	
	/** perform the HMM optimization of vocoding parameter frames, using as much context as is possible/reasonable */
	private void generateParameterFrames() {
		HTSModel htsModel = getHTSModel();
		List<HTSModel> localHMMs = new ArrayList<HTSModel>();
		/** iterates over left/right context IUs */
		SysSegmentIU contextIU = this;
		/** size of left context; increasing this may improve synthesis performance at the cost of computational effort */
		int maxPredecessors = SynthesisConfig.getDefaultInstance().getHmmOptimizationPastContext();  
		// initialize contextIU to the very first IU in the list
		while (contextIU.getSameLevelLink() != null && maxPredecessors > 0) {
			contextIU = (SysSegmentIU) contextIU.getSameLevelLink();
			maxPredecessors--;
		}
		// now traverse the predecessor IUs and add their models to the list
		int start = 0;
		while (contextIU != this && contextIU != null) {
			start += appendSllHtsModel(localHMMs, contextIU);
			contextIU = (SysSegmentIU) contextIU.getNextSameLevelLink();
		}
		localHMMs.add(htsModel);
		int length = htsModel.getTotalDur();
		awaitContinuation();
		/** deal with the right context units; increasing this may improve synthesis quality at the cost of computaitonal effort */
		int maxSuccessors = SynthesisConfig.getDefaultInstance().getHmmOptimizationFutureContext();
		if (contextIU != null)
			contextIU = (SysSegmentIU) contextIU.getNextSameLevelLink();
		while (contextIU != null && maxSuccessors > 0) {
			appendSllHtsModel(localHMMs, contextIU);
			contextIU = (SysSegmentIU) contextIU.getNextSameLevelLink();
			maxSuccessors--;
		}
		// make sure we have a paramGenerator
		PHTSParameterGeneration paramGen = new PHTSParameterGeneration(hmmdata);
		FullPStream pstream = paramGen.buildFullPStreamFor(localHMMs);
		hmmSynthesisFeatures = new ArrayList<>(length);
		// ponder whether we really want this; in particular, we probably don't want this in no-future incremental use-cases!
		boolean useMaryF0 = true;
		Queue<Double> maryF0Values = null; // one per voiced state in this segment's htsModel
		if (useMaryF0 && htsModel.getMaryXmlF0() != null) {
			maryF0Values = reconstructMaryF0FromXml(htsModel);
		}
		if (maryF0Values == null || maryF0Values.size() == 0)
			useMaryF0 = false;
		for (int i = start; i < start + length; i++) {
			FullPFeatureFrame frame = pstream.getFullFrame(i);
			if (useMaryF0) {
				Double maryF0 = maryF0Values.poll();
				if (maryF0 != null)
					frame.setf0Par(maryF0);
			}
			hmmSynthesisFeatures.add(frame);
		}
	}
	
	private Queue<Double> reconstructMaryF0FromXml(HTSModel htsModel) {
		int numVoicedFrames = htsModel.getNumVoiced();
		if (numVoicedFrames == 0)
			return null; // there's nothing to be done here!
		// maryxml f0 values come as a sequence of comma-separated number pairs in parentheses: (percentage,pitch)(percentage2,pitch2)... 
		Pattern p = Pattern.compile("(\\d+,\\d+(?:\\.\\d+)?)");
		Matcher xml = p.matcher(htsModel.getMaryXmlF0());
		Double[] f0Values = new Double[numVoicedFrames];
		Double prevValue = null;
		while (xml.find()) {
			String[] parseResult = (xml.group().trim()).split(",");
			Double value;
			try { 
				value = Double.valueOf(parseResult[1]); 
				if (prevValue == null)
					prevValue = value;
			} catch(NumberFormatException nfe) {
				value = null;
			}
			try { 
				int key = Integer.parseInt(parseResult[0]);
				if (key == 0) {
					f0Values[0] = value;
				} else if (key == 100) {
					f0Values[numVoicedFrames - 1] = value;
				} else {
					int index = (int) ((numVoicedFrames * key) / 100.0);
					if (index >= 0 && index < numVoicedFrames)
						f0Values[index] = value;
				}
			} catch(NumberFormatException nfe) {
				logger.warn("ignoring f0 spec in maryXML (cannot parse): \n", nfe);
			}
		}
		if (prevValue == null) {
			logger.warn("Could not parse any f0 value in " + htsModel.getMaryXmlF0());
			return null;
		}
		// make sure that we have a voicing value in place for every voiced frame:
		for (int i = 0; i < f0Values.length; i++) {
			if (f0Values[i] == null)
				f0Values[i] = prevValue;
			else
				prevValue = f0Values[i];
		}
		return new LinkedList<Double>(Arrays.asList(f0Values));
	}
	
	/** return the next vocoding parameter frame */
	public synchronized FullPFeatureFrame getHMMSynthesisFrame(int req) {
		if (hmmSynthesisFeatures == null) {
			generateParameterFrames();
		}
		assert req >= 0;
		setProgress(IU.Progress.ONGOING);
		assert req < durationInSynFrames() || req == 0;
		int dur = durationInSynFrames(); // the duration in frames (= the number of frames that should be there)
		int fra = hmmSynthesisFeatures.size(); // the number of frames available
		// just repeat/drop frames as necessary if the amount of frames available is not right
		int frameNumber = (int) (req * (fra / (double) dur));
		FullPFeatureFrame frame =  hmmSynthesisFeatures.get(frameNumber);
		if (req == dur - 1) { // last frame 
			setProgress(IU.Progress.COMPLETED);
//			logger.debug("completed " + deepToString());
			logger.debug("completed " + toMbrolaLine());
		}
		realizedDurationInSynFrames++;
	/*	double pitch = frame.getlf0Par();
		double meanPitch = Math.log(93.73f);
		pitch += (pitch - meanPitch) * pitchExcitationFactor;
		frame.setlf0Par(pitch);
	*/	frame.shiftlf0Par(pitchShiftInCent);
		// check whether we've been requested to wait for our continuation
		if (realizedDurationInSynFrames == durationInSynFrames())
			awaitContinuation();
		if (vocodingFramePostProcessor != null) 
			return vocodingFramePostProcessor.postProcess(this, frame);
		else
			return frame;
	}
	
	/* * * * * * * * * * * * * * * * * * * * * * * */
	/* progress handling                           */
	/* * * * * * * * * * * * * * * * * * * * * * * */
	@Override
	public IU.Progress getProgress() {
		return progress;
	}
	
	private void setProgress(IU.Progress p) {
		if (p != this.progress) { 
			this.progress = p;
			notifyListeners();
			// on completion, notify the next segment (which will in turn notify it's listeners about its own UPCOMING status)
			if (p == IU.Progress.COMPLETED && getNextSameLevelLink() != null) {
				getNextSameLevelLink().notifyListeners();
			}
		}
	}
	
	/* * * * * * * * * * * * * * * * * * * * * * * */
	/* prosodic manipulation                       */
	/* * * * * * * * * * * * * * * * * * * * * * * */
	/** 
	 * stretch the current segment by a given factor.
	 * NOTE: as time is discrete (in 5ms steps), the stretching factor that is actually applied
	 * may differ from the requested factor. 
	 * @param factor larger than 1 to lengthen, smaller than 1 to shorten; must be larger than 0
	 * @return the actual duration that has been applied 
	 */
	public double stretchFromOriginal(double factor) {
		assert factor > 0f;
		double newDuration = originalDuration() * factor;
		return setNewDuration(newDuration);
	}
	
	/** 
	 * set a new duration of this segment; if this segment is already ongoing, the 
	 * resulting duration cannot be shortend to less than what has already been going on (obviously, we can't revert the past)  
	 * @return the actual new duration (multiple of 5ms) 
	 */
	public synchronized double setNewDuration(double d) {
		assert d >= 0;
		d = Math.max(d, realizedDurationInSynFrames / 200.f); 
		if (originalLabel == null) 
			originalLabel = this.l;
		Label oldLabel = this.l;
		// change duration to conform to 5ms limit
		double newDuration = Math.round(d * 200.f) / 200.f;
		shiftBy(newDuration - duration(), true);
		// correct this' label to start where it used to start (instead of the shifted version)
		this.l = new Label(oldLabel.getStart(), this.l.getEnd(), this.l.getLabel());
		return newDuration;
	}
	
	public void setVocodingFramePostProcessor(VocodingFramePostProcessor postProcessor) {
		vocodingFramePostProcessor = postProcessor;
	}
	
	/** 
	 * change the synthesis frames' pitch curve to attain the given pitch in the final pitch
	 * in a smooth manner
	 * @param finalPitch the log pitch to be reached in the final synthesis frame 
	 */
	public void attainPitch(double finalPitch) {
		ListIterator<FullPFeatureFrame> revFrames = hmmSynthesisFeatures.listIterator(hmmSynthesisFeatures.size());
	//	finalPitch = Math.log(finalPitch);
		double difference = finalPitch - revFrames.previous().getlf0Par();
		double adjustmentPerFrame = difference / hmmSynthesisFeatures.size();
		System.err.println("attaining pitch in " + toString() + " by " + difference);
		revFrames.next();
		// adjust frames in reverse order (in case this segment is already being synthesized)
		for (; revFrames.hasPrevious(); ) {
			FullPFeatureFrame frame = revFrames.previous();
			frame.setlf0Par(frame.getlf0Par() - difference);
			difference -= adjustmentPerFrame;
		}
	}
	
	/** SysSegmentIUs has a granularity finer than 10 ms (5ms), hence output must be more precise */
	@Override
	public String toLabelLine() {
		return String.format(Locale.US,	"%.3f\t%.3f\t%s", startTime(), endTime(), toPayLoad());
	}

}
