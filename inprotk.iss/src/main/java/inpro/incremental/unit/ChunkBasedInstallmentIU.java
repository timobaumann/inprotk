package inpro.incremental.unit;


import inpro.synthesis.MaryAdapter;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;


/** 
 * an synthesizable InstallmentIU that uses chunkss to structure its output.
 * Chunks are simply sequences of words, that might or might no co-incide with intonation phrases
 * chunkss can be added even when the utterance is already being produced
 * (currently, chunkss cannot be "revoked" from the installment as that wasn't necessary in our tasks yet)
 * @author timo
 */
public class ChunkBasedInstallmentIU extends SysInstallmentIU {
	/** counts the hesitations in this installment (which need to be accounted for when counting the continuation point of a resynthesis when appending a continuation */
	private int numHesitationsInserted = 0;
	/** keeps the list of chunks that this installment is based on */
	private final IUList<ChunkIU> chunks = new IUList<ChunkIU>();
	
	public ChunkBasedInstallmentIU(HesitationIU hesitation) {
		super("<hes>", new ArrayList<WordIU>(Collections.<WordIU>singletonList((WordIU)hesitation)));
		numHesitationsInserted++;
		chunks.add(hesitation);
	}
	
	/** create a chunk from  */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public ChunkBasedInstallmentIU(ChunkIU chunk) {
		super(chunk.toPayLoad(), chunk.getWords());
		// if the chunk that we're starting from isn't yet pre-synthesized, we have to build the IU structure
		if (chunk.getWords() == null) {
			groundedIn = (List) MaryAdapter.getInstance().text2WordIUs(tts);
		} else {
			groundedIn = new ArrayList<IU>(groundedIn); // ensure that the grounded-in lists aren't shared between InstallmentIU and ChunkIU!
		}
		chunk.groundIn(groundedIn);	
		chunks.add(chunk);
	}

	/** append words for this chunk at the end of the installment */
	public void appendChunk(ChunkIU chunk) {
		WordIU oldLastWord = getFinalWord(); // everything that follows this word via fSLL belongs to the new chunk
		List<IU> chunkWords = new ArrayList<IU>();
		if (chunk instanceof HesitationIU) {
			chunk.shiftBy(oldLastWord.getLastSegment().endTime());
			chunkWords.add(chunk);
			oldLastWord.getLastSegment().addNextSameLevelLink(chunk.getFirstSegment());
			oldLastWord.addNextSameLevelLink(chunk);
			numHesitationsInserted++;
		} else {
			appendContinuation(chunk);
			while (oldLastWord.getNextSameLevelLink() != null) {
				IU w = oldLastWord.getNextSameLevelLink();
				chunkWords.add(w);
				oldLastWord = (WordIU) w;
			}
			chunk.groundIn(chunkWords);
		}
		groundedIn.addAll(chunkWords);
		chunk.setSameLevelLink(chunks.getLast());
		chunks.add(chunk);
	}
	
	/** append a continuation to the ongoing installment. 
	 * this works as follows: <pre>
	 * we have linguistic preprocessing generate a full IU structure for both the base words and the continuation
	 * we then identify the words which are the continuation part of the full structure: 
	 * we append the continuation part to the last utterance of the IU
	 * we then move backwards in the lists of segments and copy over synthesis information to the old segments
	 * we call this last step "back-substitution"
	 </pre>*/
	private void appendContinuation(ChunkIU chunk) {
		WordIU firstNewWord = null;
		// for connected prosody, resynthesize everything into a new installment, and keep only the new part (however, old parts will be back-substituted)
		// for unconnected prosody, synthesize only the new chunk 
		if (System.getProperty("proso.cond.connect", "true").equals("true")) {
			List<String> chunkTexts = new ArrayList<String>();
			// move back to the first chunkIU of the installment
			for (ChunkIU oldChunk : chunks) {
				chunkTexts.add(oldChunk.toPayLoad());
			}
			chunkTexts.add(chunk.toPayLoad());
			String fullInstallmentText = StringUtils.join(chunkTexts, " "); 
			logger.debug("querying MaryTTS for: " + fullInstallmentText);
			fullInstallmentText = fullInstallmentText.replaceAll(" <sil>", ""); // it's nasty when there are silences pronounced as "kleiner als sil größer als"
			fullInstallmentText = fullInstallmentText.replaceAll(" *<hes>", ""); // ... or hesitations as "kleiner als hes größer als"
			@SuppressWarnings({ "unchecked", "cast", "rawtypes" })
			List<WordIU> newWords = (List<WordIU>) (List) (new SysInstallmentIU(fullInstallmentText)).groundedIn();
			logger.debug("received the words" + newWords.toString());
			assert newWords.size() >= groundedIn.size() - numHesitationsInserted;
//			assert newWords.size() == groundedIn.size() + chunk.expectedWordCount(); // for some reason, this assertion breaks sometimes -> nasty stuff going on with pauses
			newWords.get(0).getSegments().get(0).shiftBy(startTime(), true);
			firstNewWord = newWords.get(groundedIn.size() - numHesitationsInserted);
		} else {
			firstNewWord = (WordIU) (new SysInstallmentIU(chunk.toPayLoad())).groundedIn().get(0);
			// change timing of new words/segments to match that of previously synthesized stuff
			firstNewWord.getSegments().get(0).shiftBy(getFinalWord().endTime() - firstNewWord.startTime(), true);
		}
		WordIU lastOldWord = getFinalWord();
		//assert lastOldWord.payloadEquals(firstNewWord.getSameLevelLink());
		SysSegmentIU newSeg = (SysSegmentIU) firstNewWord.getFirstSegment().getSameLevelLink();
		firstNewWord.connectSLL(lastOldWord);
		// back substitution just copy over the HTSModel from the new segments to the old segments (and be done with it)
		backsubstituteHTSModels(newSeg, (SysSegmentIU) lastOldWord.getLastSegment());
	}
	
	/**
	 * recursively walk backwards on the segment layer, replacing older segments with newer segments.
	 * Segments that have been generated more recently are potentially of better quality and should be used 
	 * to replace old segments (which have been generated with other or less context) wherever possible
	 * (i.e., if their synthesis hasn't started yet). 
	 */
	private void backsubstituteHTSModels(SysSegmentIU newSeg, SysSegmentIU oldSeg) {
		if (newSeg != null && oldSeg != null && newSeg.payloadEquals(oldSeg) && oldSeg.isUpcoming()) {
			oldSeg.copySynData(newSeg);
			backsubstituteHTSModels((SysSegmentIU) newSeg.getSameLevelLink(), (SysSegmentIU) oldSeg.getSameLevelLink());
		}
	}
	
	public void revokeChunk(ChunkIU chunk) {
		assert chunk.isUpcoming();
//		SegmentIU seg = ((WordIU) chunk.groundedIn()).getSegments().get(0);
//		seg.getSameLevelLink().removeAllNextSameLevelLinks();
		if (chunk instanceof HesitationIU) {
			numHesitationsInserted--;
		}
		for (WordIU word : chunk.getWords()) {
			word.setSameLevelLink(null);
			word.removeAllNextSameLevelLinks();
			groundedIn.remove(word);
			//word.revoke();
			for (SegmentIU seg : word.getSegments()) {
				seg.setSameLevelLink(null);
				seg.removeAllNextSameLevelLinks();
			}
		}
		chunks.remove(chunk);
	}

	/** breaks the segment links between words so that crawling synthesis stops after the currently ongoing word */
	public void stopAfterOngoingWord() {
		ListIterator<IU> groundIt = groundedIn.listIterator(groundedIn.size());
		for (; groundIt.hasPrevious(); ) {
			WordIU word = (WordIU) groundIt.previous();
			if (word.isCompleted()) {
				break;
			}
			// break the segmentIU layer
			SegmentIU seg = word.getLastSegment();
			seg.removeAllNextSameLevelLinks();
			// hack for long words: also stop in the middle of word
			if (word.getSegments().size() > 7) {
				word.getSegments().get(5).removeAllNextSameLevelLinks();
			}
			if (seg.isCompleted()) 
				break; // no need to go on, as this the past already
		}
	}
	
}
