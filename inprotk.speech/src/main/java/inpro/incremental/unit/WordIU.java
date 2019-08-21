package inpro.incremental.unit;

import inpro.annotation.Label;

import java.util.*;

public class WordIU extends IU {

	private final boolean isSilence;
	private final String word;
	
	public static final WordIU FIRST_WORD_IU = new WordIU() {
		@Override
		public String toPayLoad() {
			return "The very first IU";
		}
	};

	public WordIU(String token, boolean isSilence, WordIU sll, List<IU> groundedIn) {
		this(token, sll, groundedIn, isSilence);
	}
	
	public WordIU(String word, WordIU sll, List<IU> groundedIn) {
		this(word, sll, groundedIn, "<sil>".equals(word));
	}
	
	protected WordIU(String word, WordIU sll, List<IU> groundedIn, boolean isSilence) {
		super(sll, groundedIn, true);
		this.word = word;
		this.isSilence = isSilence;
	}
	
	/**
	 * create a new silent word
	 * @param sll
	 */
	public WordIU(WordIU sll) {
		super(sll, Collections.nCopies(1,
                new SyllableIU(null, Collections.nCopies(1, new SegmentIU("SIL", null)))),
			true);
		this.word = "<sil>";
		isSilence = true;
	}
	
	public WordIU() {
		this.word = "First WordIU";
		isSilence = this.word.equals("<sil>");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" }) // the untyped list in the call to Collections.checkedList
	public List<SegmentIU> getSegments() {
		List<SegmentIU> returnList;
		if ((groundedIn == null) || groundedIn.size() == 0) {
			returnList = Collections.<SegmentIU>emptyList();
		} else if (groundedIn.get(0) instanceof SegmentIU) {
			returnList = Collections.checkedList((List) groundedIn, SegmentIU.class);
		} else {
			returnList = new ArrayList<SegmentIU>();
			recursivelyAggregateSegments(groundedIn, returnList);
		}
		return returnList;
	}
	
	/** follow groundedIn links and collect all SegmentIUs along the way */ 
	private void recursivelyAggregateSegments(List<IU> grin, List<SegmentIU> returnList) {
		for (IU iu : grin) {
			if (iu instanceof SegmentIU) {
				returnList.add((SegmentIU)iu);
			} else {
				recursivelyAggregateSegments(iu.groundedIn, returnList);
			}
		}
	}
	
	public SegmentIU getFirstSegment() {
		return getSegments().get(0);
	}
	
	public SegmentIU getLastSegment() {
		List<SegmentIU> segments = getSegments();
		if (segments.size() > 0)
			return segments.get(segments.size() - 1);
		else
			return null;
	}
	
	public void updateSegments(List<Label> newLabels) {
		List<SegmentIU> segments = getSegments();
		assert (segments.size() >= newLabels.size())
			: "something is wrong when updating segments in word:"
			+ this.toString()
			+ "I was supposed to add the following labels:"
			+ newLabels
			+ "but my segments are:"
			+ segments;
		Iterator<SegmentIU> segIt = segments.iterator();
		for (Label label : newLabels) {
			segIt.next().updateLabel(label);
		}
		notifyListeners();
	}
	
	public boolean spellingEquals(WordIU iu) {
		return (iu != null) && (getWord().equals(iu.getWord()));
	}
	
	public String getWord() {
		return word;
	}
	
	/** shift the start and end times of this (and possibly all following SysSegmentIUs */
	public void shiftBy(double offset) {
		for (SegmentIU segment : getSegments()) {
			segment.shiftBy(offset, false);
		}
	}
	
	public boolean isSilence() {
		return isSilence;
	}
	
	@Override
	public String toPayLoad() {
		return getWord();
	}
	
	/** 
     * Builds a simple string from a list of wordIUs
	 * @return a string with the contained words separated by whitespace
	 */
	public static String wordsToString(List<WordIU> words) {
		StringBuilder ret = new StringBuilder();
		for (WordIU iu : words) {
			if (!iu.isSilence()) {
				ret.append(iu.getWord());
				ret.append(" ");				
			}
		}
		return ret.toString().replaceAll("^ *", "").replaceAll(" *$", "");
	}

	public StringBuilder toMbrolaLines() {
		StringBuilder sb = new StringBuilder("; ");
		sb.append(toPayLoad());
		sb.append("\n");
		for (SegmentIU seg : getSegments()) {
			sb.append(seg.toMbrolaLine());
		}
		return sb;
	}
	
	/** returns a new list with all silent words removed */
	public static List<WordIU> removeSilentWords(List<WordIU> words) {
		List<WordIU> outList = new ArrayList<WordIU>(words);
		Iterator<WordIU> iter = outList.iterator();
		while (iter.hasNext()) {
			if (iter.next().isSilence())
				iter.remove();
		}
		return outList;
	}
	
	public static boolean spellingEqual(List<WordIU> a, List<WordIU> b) {
		boolean equality = (a.size() == b.size());
		if (equality) // only bother if lists are of same size
			for (int i = 0; i < a.size(); i++)
				if (!a.get(i).spellingEquals(b.get(i)))
					equality = false;
		return equality;
	}
	
	/** 
	 * calculate the WER between two lists of words based on the levenshtein distance 
	 * code adapted from http://www.merriampark.com/ldjava.htm
	 * list a is taken as the reference for word error rate normalization 
	 */
	public static double getWER(List<WordIU> a, List<WordIU> b) {
		if (a == null || b == null)
			throw new IllegalArgumentException("Strings must not be null");
		int n = a.size();
		int m = b.size();
		// handle special cases with empty lists 
		if (n == 0)
			return m;
		else if (m == 0)
			return n;
		int p[] = new int[n + 1]; // 'previous' cost array, horizontally
		int c[] = new int[n + 1]; // cost array, horizontally
		// indexes into strings s and t
		// initialize costs
		for (int i = 0; i <= n; i++) {
			p[i] = i;
		}
		for (int j = 1; j <= m; j++) {
			WordIU bCurrent = b.get(j - 1);
			c[0] = j;
			for (int i = 1; i <= n; i++) {
				int cost = a.get(i - 1).spellingEquals(bCurrent) ? 0 : 1;
				// minimum of cell to the left+1, to the top+1, diagonally left
				// and up + cost
				c[i] = min(c[i - 1] + 1, 
						   p[i] + 1, 
						   p[i - 1] + cost);
			}
			// swap current and previous cost arrays
			int s[];
			s = p;
			p = c;
			c = s;
		}
		return ((double) p[n]) / a.size();
	}
	
	/** three-way minimum used internally in getWER */
	private static final int min(int a, int b, int c) {
		return Math.min(Math.min(a, b), c);
	}
	
}
