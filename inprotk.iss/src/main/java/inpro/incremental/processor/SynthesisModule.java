package inpro.incremental.processor;

import inpro.audio.DispatchStream;
import inpro.incremental.IUModule;
import inpro.incremental.unit.*;
import inpro.incremental.unit.IU.IUUpdateListener;
import inpro.incremental.unit.IU.Progress;
import inpro.synthesis.MaryAdapter;
import inpro.util.TimeUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SynthesisModule extends IUModule {

	protected DispatchStream speechDispatcher;
	
	protected ChunkBasedInstallmentIU currentInstallment;
	
	private Any2ChunkIUWrapper words2chunk = new Any2ChunkIUWrapper(); 
	
	/** 
	 * This constructor is necessary for use with Configuration Management. 
	 * You should rather use SynthesisModule(SimpleMonitor.setupDispatcher()) 
	 * if you need to create your own synthesis module. 
	 */
	public SynthesisModule() {
		this(null);
	}
	
	/**
	 * @param speechDispatcher if in doubt, create one by calling SimpleMonitor.setupDispatcher()
	 */
	public SynthesisModule(DispatchStream speechDispatcher) {
		this.speechDispatcher = speechDispatcher;
		MaryAdapter.getInstance(); // preload mary
	}
	
	/**
	 * please only send ChunkIUs or WordIUs; everything else will result in assertions failing
	 */
	@Override
	protected synchronized void leftBufferUpdate(Collection<? extends IU> ius,
			List<? extends EditMessage<? extends IU>> edits) {
		boolean startPlayInstallment = false;
		for (EditMessage<?> em : edits) {
			logger.debug(em.getType() + " " + em.getIU().toPayLoad());
			ChunkIU chunkIU = words2chunk.getChunkIU(em.getIU());
			switch (em.getType()) {
			case ADD:
				if (currentInstallment != null && !currentInstallment.isCompleted()) {
					WordIU choiceWord = currentInstallment.getFinalWord();
					// mark the ongoing utterance as non-final, (includes check whether there's still enough time)
					boolean canContinue = ((SysSegmentIU) choiceWord.getLastSegment()).setAwaitContinuation(true);
					if (canContinue) {
						currentInstallment.appendChunk(chunkIU);
					} else { 
						currentInstallment = null;
					}
				} 
				if (currentInstallment == null || currentInstallment.isCompleted()) { // start a new installment
					if (chunkIU instanceof HesitationIU)
						currentInstallment = new ChunkBasedInstallmentIU((HesitationIU) chunkIU);
					else
						currentInstallment = new ChunkBasedInstallmentIU(chunkIU);
					// shift forward this installment by the time since system startup
					currentInstallment.getSegments().get(0).shiftBy(TimeUtil.timeSinceStartup(), true);
					startPlayInstallment = true;
				}
				appendNotification(currentInstallment, chunkIU);
				break;
			case REVOKE:
				if (currentInstallment == null || currentInstallment.isCompleted()) {
					logger.warn("chunk " + chunkIU + " was revoked but installment has been completed already, can't revoke anymore.");
				} else {
					if (!chunkIU.isUpcoming()) {
						logger.warn("chunk " + chunkIU + " is not upcoming anymore, can't revoke anymore. (send us an e-mail if you really need to revoke ongoing chunks)");
					} else {
						currentInstallment.revokeChunk(chunkIU);
					}
				}
				break;
			case COMMIT:
				// ensure that this chunk can be finished
				chunkIU.setFinal();
				if (currentInstallment == null) 
					break; // do nothing if the installment has been discarded already
				for (SysSegmentIU seg : currentInstallment.getSegments()) {
					// clear any locks that might block the vocoder from finishing the utterance chunk
					seg.setAwaitContinuation(false);			
				}
				//currentInstallment = null; 
				startPlayInstallment = false; // to avoid a null pointer
				break;
			default:
				break;
			}
		}
		if (startPlayInstallment) {
			assert speechDispatcher != null;
			speechDispatcher.playStream(currentInstallment.getAudio(), false);
		}
		if (currentInstallment != null)
			rightBuffer.setBuffer(currentInstallment.getWords());
	}
	
	private void appendNotification(SysInstallmentIU installment, ChunkIU chunk) {
		IUUpdateListener listener = new NotifyCompletedOnOngoing(chunk);
		// add listener to first segment for UPCOMING/ONGOING updates
		chunk.getFirstSegment().addUpdateListener(listener);
		String updateposition = System.getProperty("proso.cond.updateposition", "end");
		if (updateposition.equals("end")) 
			installment.getFinalWord()
					.getLastSegment().getSameLevelLink() // attach to second-to-last segment of the last word
					.addUpdateListener(listener);
		else if (updateposition.equals("-1word"))
			((WordIU) installment.getFinalWord().getSameLevelLink())
			.getLastSegment().getSameLevelLink()
			.addUpdateListener(listener);
		else {
			int req;
			if (updateposition.equals("+1word"))
				req = 0;
			else if (updateposition.equals("+2word"))
				req = 1;
			else if (updateposition.equals("+3word"))
				req = 2;
			else
				throw new RuntimeException("proso.cond.updateposition was set to the invalid value " + updateposition);
				
			if (chunk.groundedIn().size() <= req) {
				logger.warn("cannot update on " + req + ", will update on " + (chunk.groundedIn().size() - 1) + " instead");
				req = chunk.groundedIn().size() - 1;
			}
			((WordIU) chunk.groundedIn().get(req))
			.getLastSegment().getSameLevelLink()
			.addUpdateListener(listener);
		}
	}
	
	/** notifies the given ChunkIU when the IU this is listening to is completed */
	class NotifyCompletedOnOngoing implements IUUpdateListener {
		ChunkIU completed;
		Progress previousProgress = null;
		NotifyCompletedOnOngoing(ChunkIU notify) {
			completed = notify;
		}
		/** @param updatingIU the SegmentIU that this listener is attached to in {@link SynthesisModule#appendNotification(SysInstallmentIU, ChunkIU)} */
		@Override
		public void update(IU updatingIU) {
			if (updatingIU.isCompleted() && updatingIU != completed.getFirstSegment()) { // make sure that the chunk is marked as completed even though not necessarily the last segment has been completed
                completed.setProgress(Progress.COMPLETED);
                previousProgress = completed.getProgress();
            }
			if (!completed.getProgress().equals(previousProgress))
				completed.notifyListeners();
			previousProgress = completed.getProgress();
		}
	}
	
	class Any2ChunkIUWrapper {
		Map<WordIU,ChunkIU> map = new HashMap<WordIU,ChunkIU>();
		ChunkIU getChunkIU(WordIU w) {
			if (!map.containsKey(w)) {
				if ("<hes>".equals(w.toPayLoad()))
					map.put(w,  new HesitationIU());
				else 
					map.put(w, new ChunkIU(w));
			}
			return map.get(w);
		}
		ChunkIU getChunkIU(IU iu) {
			return (iu instanceof ChunkIU) ? (ChunkIU) iu : getChunkIU((WordIU) iu);
		}
	}
	
}
