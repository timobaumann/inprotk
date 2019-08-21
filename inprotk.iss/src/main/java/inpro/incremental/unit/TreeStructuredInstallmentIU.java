package inpro.incremental.unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * multiple synthesis paths organized in a tree structure.
 * this is incredibly inefficient if you've got many options. 
 * the XX YY ZZ AA BB CC (with each a few options) results in plenty possible variants
 * at the same time, this doesn't even allow for lengthening/speed changes.
 * However, it allows for zero-latency switching between hypotheses.  
 * @author timo
 */
public class TreeStructuredInstallmentIU extends SysInstallmentIU {

	public TreeStructuredInstallmentIU(String base) {
		super(base);
	}
	
	public TreeStructuredInstallmentIU(List<String> variants) {
		this(variants.get(0));
		for (int i = 1; i < variants.size(); i++) {
			addAlternativeVariant(variants.get(i));
		}
	}
	
	public void addAlternativeVariant(String variant) {
		variant = variant.replaceAll(" <sil>", ""); // it's nasty when there are silences pronounced as "kleiner als sil größer als"
		SysInstallmentIU varInst = new SysInstallmentIU(variant);
		WordIU commonWord = getInitialWord();
		// variant word
		WordIU varWord = varInst.getInitialWord();
		assert (commonWord.spellingEquals(varWord)) : "installment variants must all start with the same word!";
		boolean hasVarWord = true;
		while (hasVarWord) {
			hasVarWord = false;
			varWord = (WordIU) varWord.getNextSameLevelLink();
			for (IU nextIU : commonWord.getNextSameLevelLinks()) {
				WordIU nextWord = (WordIU) nextIU;
				if (nextWord.spellingEquals(varWord)) {
					hasVarWord = true;
					commonWord = nextWord;
					break; // next while loop
				}
			}
		}
		if (varWord != null) {
			varWord.connectSLL(commonWord);
			WordIU groundingWord = varWord;
			while (groundingWord != null) {
				groundedIn.add(groundingWord);
				groundingWord = (WordIU) groundingWord.getNextSameLevelLink();
			}
			// now shift segment times for the variant to match that of the common root
			SysSegmentIU firstVarSegment = (SysSegmentIU) varWord.getSegments().get(0);
			SegmentIU lastCommonSegment = commonWord.getLastSegment();
			firstVarSegment.shiftBy(lastCommonSegment.endTime() - firstVarSegment.startTime(), true);
		}
	}
	
	public List<WordIU> getWordsAtPos(int pos) {
		WordIU wordIU = getInitialWord();
		return getWordsAtPos(Collections.singletonList(wordIU), pos);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static List<WordIU> getWordsAtPos(List<WordIU> parentWords, int i) {
		if (i == 0) {
			return parentWords;
		} else {
			List<WordIU> daughterWords = new ArrayList<WordIU>();
			for (WordIU parent : parentWords) {
				List<WordIU> daughters = (List) parent.getNextSameLevelLinks();
				if (daughters != null)
					daughterWords.addAll(daughters);
			}
			return getWordsAtPos(daughterWords, i - 1);
		}
	}
	
	@Override
	public String toString() {
		return getInitialWord().toTreeViaNextSLLString();
	}
	
}