package inpro.incremental.processor;

import inpro.audio.DispatchStream;
import inpro.incremental.unit.EditMessage;
import inpro.incremental.unit.IU;
import inpro.incremental.unit.SysSegmentIU;
import inpro.incremental.unit.WordIU;
import inpro.synthesis.hts.VocodingFramePostProcessor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

public class AdaptableSynthesisModule extends SynthesisModule {
	
	VocodingFramePostProcessor framePostProcessor = null;
	
	/** call this to set up a synthesis module; you may use SimpleMonitor.setupDispatcher() for a default dispatcher */
	public AdaptableSynthesisModule(DispatchStream ds) {
        super(ds);
    }

	@SuppressWarnings("unchecked")
	public void pauseAfterOngoingWord() {
		synchronized(currentInstallment) {
			if (currentInstallment != null) {
				List<WordIU> words = (List<WordIU>) currentInstallment.groundedIn();
				ListIterator<WordIU> wordIt = words.listIterator(words.size());
				for (; wordIt.hasPrevious(); ) {
					SysSegmentIU wordStartSeg = (SysSegmentIU) wordIt.previous().getFirstSegment();
					wordStartSeg.setAwaitContinuation(true);
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void resumePausedSynthesis() {
		synchronized(currentInstallment) {
			if (currentInstallment != null) {
				List<WordIU> words = (List<WordIU>) currentInstallment.groundedIn();
				ListIterator<WordIU> wordIt = words.listIterator(words.size());
				for (; wordIt.hasPrevious(); ) {
					SysSegmentIU wordStartSeg = (SysSegmentIU) wordIt.previous().getFirstSegment();
					wordStartSeg.setAwaitContinuation(false);
				}
			}
		}
	}
	
	/** stop the ongoing (uncommitted) utterance after the ongoing word */
	public void stopAfterOngoingWord() {
		synchronized(currentInstallment) { // TODO: find out why this is synchronized
			if (currentInstallment != null)
				currentInstallment.stopAfterOngoingWord();
			// NOTE: the following two lines first resume synthesis and afterwards discard
			// NOTE: the stream that is played which is necessary for paused installments. 
			// NOTE: TODO: This may lead to (short) audible noise.
			// NOTE: However, changing the order of the lines is impossible as skipping a stream 
			// NOTE: in that situation becomes impossible because of deadlock (synchronized(this) in DispatchStream)
			resumePausedSynthesis();
			speechDispatcher.clearStream(); 
			currentInstallment = null;
		}
	}
	
	public void stopAfterOngoingPhoneme() {
		for (SysSegmentIU seg : getSegments()) {
			seg.setSameLevelLink(null);
			seg.removeAllNextSameLevelLinks();
		}
		currentInstallment = null;
	}
	
	@Override
	protected synchronized void leftBufferUpdate(Collection<? extends IU> ius,
			List<? extends EditMessage<? extends IU>> edits) {
		super.leftBufferUpdate(ius, edits);
		for (SysSegmentIU seg : getSegments())
			seg.setVocodingFramePostProcessor(framePostProcessor);
	}
	
	/** return the segments in the ongoing utterance (if any) */ 
	private List<SysSegmentIU> getSegments() {
		if (currentInstallment != null)
			return currentInstallment.getSegments();
		else
			return Collections.<SysSegmentIU>emptyList();
	}

	/**
	 * @param s absolute scaling, that is, applying s=2 multiple times does not result in multiple changes
	 */
	public void scaleTempo(double s) {
		for (SysSegmentIU seg: getSegments()) {
			if (!seg.isCompleted()) {
				seg.stretchFromOriginal(s);
			}
		}
	}
	
	/**
	 * @param pitchShiftInCent cent is 1/100 of a halftone. 1200 cent is an octave
	 */
	public void shiftPitch(int pitchShiftInCent) {
		for (SysSegmentIU seg: getSegments()) {
			if (!seg.isCompleted()) {
				seg.pitchShiftInCent = pitchShiftInCent;
			}
		}
	}
	
	public void setFramePostProcessor(VocodingFramePostProcessor postProcessor) {
		this.framePostProcessor = postProcessor;
	}
	
}
