package inpro.incremental.unit;


import inpro.synthesis.MaryAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 
 * an IU that binds several WordIUs together to form a chunk.
 * Chunks are simply sequences of words, ideally co-inciding with intonation phrases
 * chunks are a convenient level for synthesis output if they contain words in 
 * units that roughly correspond to prosodic phrases
 * @author timo
 */
public class ChunkIU extends WordIU {

	final String chunkText;
	private ChunkType type;
	/** the state of delivery that this unit is in */
	Progress progress = null;
	
	public enum ChunkType {
	    NONFINAL, // we're still awaiting more content in this utterance
	    FINAL, // last chunks of the utterance <-- this is not exclusive with e.g. continuation,initial,repair,
	    UNDEFINED // dunno
	}
	
	public ChunkIU(WordIU word) {
		this(word.toPayLoad());
		word.groundIn(this);
		this.setFinal();
	}
	
	public ChunkIU(String chunkText) {
		this(chunkText, ChunkType.UNDEFINED);
	}
	
	public ChunkIU(String chunkText, ChunkType type) {
		super(chunkText, null, null);
		this.chunkText = chunkText;
		this.type = type;
	}
	
	/** constructor called from PhraseIU constructor */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected ChunkIU(List<WordIU> words, IU sameLevelLink, String chunkText) {
		super("", (WordIU) sameLevelLink, (List) words);
		this.chunkText = chunkText;
	}
	
	@Override
	public String toPayLoad() {
		return chunkText;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" }) // the untyped list in the call to Collections.checkedList
	public List<WordIU> getWords() {
		if (groundedIn != null)
			return Collections.checkedList((List) groundedIn, WordIU.class);
		else
			return null;
	}
	
	/** grounds in the list of wordIUs, potentially replacing previously grounding words */
	@Override
	public void groundIn(List<IU> ius) {
		if (groundedIn != null)
			groundedIn.clear();
		else 
			groundedIn = new ArrayList<IU>();
		groundedIn.addAll(ius);
	}

	public void setProgress(Progress p) {
		if (p != this.progress) { 
			this.progress = p;
			notifyListeners();
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void preSynthesize() {
		assert previousSameLevelLink == null : "You shouldn't pre-synthesize something that is already connected";
		assert groundedIn == null : "You shouldn't pre-synthesize something that is already connected";
		groundedIn = (List) MaryAdapter.getInstance().text2WordIUs(this.chunkText);
	}
	
	@Override
	public Progress getProgress() {
		return progress != null ? progress : super.getProgress();
	}

	public void setFinal() {
		this.type = ChunkIU.ChunkType.FINAL;
		for (SegmentIU seg : getSegments()) {
			((SysSegmentIU) seg).setAwaitContinuation(false);			
		}
	}

	public ChunkType getType() {
		return type;
	}
}
