package inpro.incremental.unit;

import java.util.List;

public class PhraseIU extends ChunkIU {

	public PhraseIU(List<WordIU> words, String phraseText, String tone, int breakIndex) {
		super(words, null, phraseText);
		setUserData("tone", tone);
		setUserData("breakIndex", breakIndex);
	}

}