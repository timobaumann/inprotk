package inpro.incremental.unit;

import inpro.annotation.Label;

import java.util.*;


public class SegmentIU extends IU {
	
	public enum SegmentType {
		SILENCE, 
		VOWEL, 
		DIPHTHONG,
		NASAL,
		LIQUID, 
		PLOSIVE,
		FRICATIVE
    }
	
	public static final Map<String, SegmentType> TYPE_MAPPING;
	static {
		Map<String, SegmentType> types = new HashMap<String, SegmentType>();
		for (String s : Label.SILENCE) {
			types.put(s, SegmentType.SILENCE);
		}
		for (String v : Arrays.asList("2:", "9", "@", "6", 
									  "a", "a:", "aa:", "A:", "aa", "A", 
				                      "e", "e:", "ee", "E", "ee:", "E:",
				                      "i:", "i", "ii", "I",
				                      "o", "o:", "oo", "O", 
				                      "u:", "u", "uu", "U",
				                      "y:", "y", "yy", "YY")) {
			types.put(v, SegmentType.VOWEL);
		}
		for (String d : Arrays.asList("aU", "ei", "eI", "oy", "OY", "ui", "uI")) { 
			types.put(d, SegmentType.DIPHTHONG);
		}
		for (String n : Arrays.asList("m", "n", "nn", "N")) { 
			types.put(n, SegmentType.NASAL);
		}
		for (String l : Arrays.asList("j", "l", "r", "rr", "R")) { 
			types.put(l, SegmentType.LIQUID);
		}
		for (String p : Arrays.asList("b", "d", "g", "p", "t", "k", "?", "qq", "Q")) { 
			types.put(p, SegmentType.PLOSIVE);
		}
		for (String f : Arrays.asList("f", "v", "s", "ss", "S", "z", "Z", "cc", "C", "x", "h", "ts", "tS", "pf")) { // also contains affricates
			types.put(f, SegmentType.FRICATIVE);
		}
		TYPE_MAPPING = Collections.unmodifiableMap(types);
	}
	
	public static final Set<String> VOWELS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
			"2:", "9", "@", "@U", "6", "a", "a:", "aa:", "A:", "aa", "A", "ai", "aI", "au", "aU", 
			"e", "e:", "ee", "E", "ee:", "E:", "ei", "eI", 
			"i:", "i", "ii", "I", "o", "o:", "oo", "O", "oy", "OY", "u:", "u", "ui", "uI", "ui:", "uu", "U", 
			"y:", "y", "yy", "Y"
/*,// for english:
			"{" // possibly more; just run without assertions for english :-) /**/
			)));
// for swedish:
/*			"A", "`A", "'A", "'A:", "''A", "''A:", "Å", "Å:", "`Å:", "'Å", "'Å:", "''Å", "''Å:", "Ä", "`Ä", "`Ä:", "'Ä", "'Ä:", "''Ä", "''Ä:", "A:_1", "'Ä3", "Ä4", "'Ä4", "''Ä4", 
			"E", "E:", "`E:", "'E", "'E:", "''E", "''E:", "E0", "'E0", 
			"I", "I:", "'I", "'I:", "''I", "''I:", 
			"O", "'O", "'O:", "''O:", "''Ö:", "'Ö:", "'Ö3", "''Ö3", "Ö4", "''Ö4", 
			"U", "U:", "'U", "'U:", "''U", "''U:", "'Y", "'Y:", "''Y")));
*/
	public static final Set<String> CONSONANTS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
			"b", "cc", "C", "d", "f", "pf", "g", "h", "j", "k", "l", 
			"m", "n", "nn", "N", "p", "qq", "?", "Q", "r", "rr", "R", 
			"s", "ss", "S", "t", "ts", "tS", "v", "x", "z", "Z")));
// for swedish:
/*			"b", "B", "d", "D", 
			"F", "g", "G", "H", 
			"J", "k", "K", "L", "M", "''M", "N", "NG", 
			"p", "P", "R", "S", 
			"SJ", "t", "T", 
			"V")));
*/
	// we keep start time, end time and text of the segment in a label
	Label l;
	
	public SegmentIU(String segment, SegmentIU sll) {
		super(sll);
		//assert (Label.SILENCE.contains(segment) || VOWELS.contains(segment) || CONSONANTS.contains(segment)) : "segment " + segment + " is neither a vowel, consonant nor silence I could understand.";
		this.l = new Label(segment);
	}
	
	public SegmentIU(Label l) {
		//assert (Label.SILENCE.contains(l.getLabel()) || VOWELS.contains(l.getLabel()) || CONSONANTS.contains(l.getLabel())) : "segment " + l.getLabel() + " is neither a vowel, consonant nor silence I could understand.";
		this.l = l;
	}

	public void updateLabel(Label l) {
		//assert (this.l.getLabel().equals(l.getLabel())) : "my label is " + this.l.toString() + ", was asked to update with " + l.toString();
		boolean needsUpdate = !this.l.equals(l);
		this.l = l;
		if (needsUpdate)
			notifyListeners();
	}
	
	@Override
	public double startTime() {
		return l.getStart();
	}
	
	@Override
	public double endTime() {
		return l.getEnd();
	}

	public boolean isSilence() {
		return l.isSilence();
	}
	
	public boolean isVowel() {
		return VOWELS.contains(l.getLabel());
	}
	
/* this code is helpful for prosodic feature extraction * /
	@Override
	public void update(EditType edit) {
		if (edit == EditType.COMMIT) {
			double time = startTime() + 1 * ResultUtil.FRAME_TO_SECOND_FACTOR;
			for (; time <= endTime() + 0.00001; time += 1 * ResultUtil.FRAME_TO_SECOND_FACTOR) { 
				System.err.printf(Locale.US, 
				                  "%.2f\t%f\t%f\t%f\t%f\t%f\t\"%s\"\t%s\n", 
				                  time, 
				                  bd.getLoudness(time),
				                  bd.getPitchInCent(time),
				                  bd.getVoicing(time),
				                  bd.getSpectralTilt(time),
				                  bd.getSpectralTiltQual(time),
				                  l.getLabel(),
				                  isVowel() ? 'V' : isSilence() ? 'S' : 'C');
			}
		}
	}
/**/

	@Override
	public String toPayLoad() {
		return l.getLabel();
	}

	/** this segment represented as an mbrola line */
	public StringBuilder toMbrolaLine() {
		StringBuilder sb = new StringBuilder(l.toMbrola());
		sb.append("\n");
		return sb;
	}

	public SegmentType type() {
		return TYPE_MAPPING.get(this.l.getLabel());
	}
	
	public void shiftBy(double offset, boolean recurse) {
		Label l = this.l;
		this.l = new Label(l.getStart() + offset, l.getEnd() + offset, l.getLabel());
		if (recurse) {
			for (IU nSll : getNextSameLevelLinks()) {
				((SegmentIU) nSll).shiftBy(offset, recurse);
			}
		}
	}
	
}
