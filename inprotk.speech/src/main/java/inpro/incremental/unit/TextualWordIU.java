package inpro.incremental.unit;

public class TextualWordIU extends WordIU {

	public static final TextualWordIU FIRST_ATOMIC_WORD_IU = new TextualWordIU("");
	public int counter;
	
	/* a word that hides any and all grounded-in hierarchy */
	/* and instead bases timing on word count */
	
	public TextualWordIU(String word, TextualWordIU sll, int counter) {
		super(word, sll, null);
		this.counter = counter;
	}
	
	public TextualWordIU(String word, TextualWordIU sll) {
		this(word, sll, (sll != null) ? sll.counter + 1 : 1);
	}
	
	private TextualWordIU(String word) {
		this(word, null);
	}
	
	@Override
	public double endTime() {
		return counter;
	}
	
	@Override
	public double startTime() {
		return (previousSameLevelLink == null) ? Double.NaN : previousSameLevelLink.endTime();
	}
	
	public boolean pronunciationEquals(WordIU iu) {
		return spellingEquals(iu);
	}
	
	@Override
	public String getWord() {
		return super.getWord().replaceAll("[\\+\\-]$", "");
	}
	
}
