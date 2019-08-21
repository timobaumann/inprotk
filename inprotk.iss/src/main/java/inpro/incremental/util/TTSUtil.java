package inpro.incremental.util;

import inpro.annotation.Label;
import inpro.incremental.unit.*;
import inpro.synthesis.hts.SynthesisPayload;
import inpro.util.TimeUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.util.JAXBResult;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * utility functions to build IU sub-networks from MaryXML
 */
public class TTSUtil {
	
	private static AllContent mary2content(InputStream is) {
		AllContent content;
		// I don't quite understand what the security manager is for when it can just arbitrarilly be disabled. but this sure gets around too-tight security issues...
		SecurityManager sm = System.getSecurityManager();
		System.setSecurityManager(null);
		try {
			JAXBContext context = JAXBContext.newInstance(AllContent.class);
			JAXBResult result = new JAXBResult(context);
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer t;
			is.mark(Integer.MAX_VALUE);
			t = tf.newTransformer(new StreamSource(TTSUtil.class.getResourceAsStream("mary2simple.xsl")));
			t.transform(new StreamSource(is), result);
			content = (AllContent) result.getResult(); //unmarshaller.unmarshal(is);
		} catch (Exception te) {
			try {
				is.reset();
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException("Cannot reset stream");
			}
			System.err.print(is);
			te.printStackTrace();
			throw new RuntimeException(te);
		}
		System.setSecurityManager(sm);
		//useful for debugging purposes:
//		System.err.println(content.toString());
		return content;
	}
	
	public static List<WordIU> wordIUsFromMaryXML(InputStream is, List<SynthesisPayload> synthesisPayload) {
		AllContent content = mary2content(is);
		List<WordIU> words = null;
		try {
			words =  content.getWordIUs(synthesisPayload != null ? synthesisPayload.iterator() : Collections.emptyIterator());
		} catch (AssertionError ae) {
			System.err.println(content.toString());
			throw ae;
		}
		// remove utterance final silences
		ListIterator<WordIU> fromEnd = words.listIterator(words.size());
		while (fromEnd.hasPrevious()) {
			WordIU last = fromEnd.previous();
			if (last.isSilence()) {
				fromEnd.remove();
			} else {
				break;
			}
		}
		fromEnd.next().removeAllNextSameLevelLinks();
		return words;
	}
	
	public static List<PhraseIU> phraseIUsFromMaryXML(InputStream is, List<SynthesisPayload> synthesisPayload, boolean connectPhrases) {
		AllContent content = mary2content(is);
		List<PhraseIU> phrases = content.getPhraseIUs(synthesisPayload != null ? synthesisPayload.iterator() : Collections.emptyIterator(), connectPhrases);
		return phrases;
	}
	
	@XmlRootElement(name = "all")
	static class AllContent {
		@XmlElement(name = "phr")
		private List<Phrase> phrases;

		@Override
		public String toString() {
			return phrases.toString();
		}
		
		public List<PhraseIU> getPhraseIUs(Iterator<SynthesisPayload> spIterator, boolean connect) {
			List<PhraseIU> phraseIUs = new ArrayList<PhraseIU>(phrases.size());
			IU prev = null;
			for (Phrase phrase : phrases) {
				PhraseIU pIU = phrase.toIU(spIterator);
				if (connect) {
					if (prev != null && Math.abs(pIU.startTime() - prev.endTime()) > 0.01) {
						pIU.shiftBy(prev.endTime() - pIU.startTime());
					}
					pIU.connectSLL(prev);
				}
				phraseIUs.add(pIU);
				prev = pIU;
			}
			return phraseIUs;
		}
		
		public List<WordIU> getWordIUs(Iterator<SynthesisPayload> spIterator) {
			List<PhraseIU> phraseIUs = getPhraseIUs(spIterator, true);
			List<WordIU> wordIUs = new ArrayList<WordIU>();
			for (PhraseIU phraseIU : phraseIUs) {
				wordIUs.addAll(phraseIU.getWords());
			}
			return wordIUs;
		}
	}
	
	@XmlRootElement(name = "phr")
	private static class Phrase {
		@XmlMixed
		private List<String> tokenList;
		@XmlAttribute private String tone;
		@XmlAttribute private int breakIndex;
		@XmlAttribute private String pitchOffset;
		@XmlAttribute private String pitchRange;
		@XmlElement(name = "t")
		private List<Word> words;

		@SuppressWarnings("unused")
		public void afterUnmarshal(Unmarshaller unmarshaller, Object parent) {
			List<Word> newWords = new ArrayList<Word>(words.size());
			// filter out the occasional empty words (which are caused by punctuation) 
			for (Word word : words) {
				if (!word.isEmpty())
					newWords.add(word);
			}

			words = newWords;
		}
		
		String phraseText() {
			String retVal = "";
			for (Word w : words) {
				if (!w.isBreak() && !retVal.equals("")) {
					retVal += " ";
				}
				retVal += w.token;
			}
			return retVal;
		}

		public PhraseIU toIU(Iterator<SynthesisPayload> spIterator) {
			List<WordIU> wordIUs = new ArrayList<WordIU>(words.size());
			WordIU prev = null;
			for (Word w : words) {
				WordIU wIU = w.toIU(spIterator);
				wIU.connectSLL(prev);
				wordIUs.add(wIU);
				prev = wIU;
			}
			return new PhraseIU(wordIUs, phraseText(), tone, breakIndex);
		}

		@Override
		public String toString() {
			return words.toString();
		}

	}
	
	@XmlRootElement(name = "t")
	private static class Word {
		@XmlMixed
		private List<String> tokenList;
		private transient String token;
		@XmlAttribute private boolean isBreak;
		@XmlAttribute private String pos; 
		@XmlElement(name = "syl")
		private List<Syllable> syllables;
		
		@SuppressWarnings("unused")
		public void afterUnmarshal(Unmarshaller unmarshaller, Object parent) {
			if (tokenList != null) {
				token = joinList(tokenList, " ").toString().replace('\n', ' ').trim();
			} else {
				token = null;
			}
			if (isBreak())
				token = "<sil>";
		}
		
		@SuppressWarnings("unused")
		public void beforeMarshal(Marshaller marshaller) {
			if (token != null) {
				tokenList = Arrays.asList(token.split("\n"));
			} else {
				tokenList = null;
			}
		}
		
		public boolean isEmpty() {
			return syllables == null || syllables.isEmpty();
		}
		
		public boolean isBreak() {
			return isBreak;
		}
		
		@Override
		public String toString() {
			return "; " + token + "\n" + ((syllables != null) ? syllables.toString() : "");
		}
		
		public WordIU toIU(Iterator<SynthesisPayload> spIterator) {
			List<IU> syllableIUs = new ArrayList<IU>(syllables.size());
			IU prev = null;
			for (Syllable s : syllables) {
				IU sIU;
				try {
					sIU = s.toIU(spIterator);
				} catch (AssertionError ae) {
					System.err.println("Error within word " + this.toString());
					throw ae;
				}
				sIU.connectSLL(prev);
				syllableIUs.add(sIU);
				prev = sIU;
			}
			WordIU wiu = new WordIU(token, isBreak(), null, syllableIUs);
			wiu.setUserData("pos", pos);
			return wiu;
		}
	}
	
	@XmlRootElement(name = "syl") 
	private static class Syllable {
		@XmlAttribute private String stress;
		@XmlAttribute private String accent;
		@XmlElement(name = "seg")
		private List<Segment> segments;
		
		@Override
		public String toString() {
			return "stress:" + stress + ", accent:" + accent + "\n" + segments.toString();
		}

		public SyllableIU toIU(Iterator<SynthesisPayload> spIterator) {
			List<IU> segmentIUs = new ArrayList<IU>(segments.size());
			IU prev = null;
			for (Segment s : segments) {
				IU sIU = s.toIU(spIterator);
				sIU.setSameLevelLink(prev);
				segmentIUs.add(sIU);
				prev = sIU;
			}
			SyllableIU siu = new SyllableIU(null, segmentIUs);
			siu.setUserData("stress", stress);
			siu.setUserData("accent", accent);
			return siu;
		}
	}
	
	@XmlRootElement(name = "seg")
	private static class Segment {
		@XmlAttribute(name = "d")
		private int duration;
		@XmlAttribute(name = "end")
		private double endTime;
		@XmlAttribute(name = "p")
		private String sampaLabel;
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(sampaLabel);
			sb.append(" ");
			sb.append(duration);
			sb.append("\n");
			return sb.toString();
		}
		
		public SysSegmentIU toIU(Iterator<SynthesisPayload> spIterator) {
			Label l = new Label(endTime - (duration / TimeUtil.SECOND_TO_MILLISECOND_FACTOR), endTime, sampaLabel);
			SysSegmentIU segIU;
			assert spIterator != null;
			if (spIterator.hasNext()) { // the HMM case
				SynthesisPayload sp = spIterator.next();
				assert (sampaLabel.equals(sp.getHTSModel().getPhoneName())) : " oups, wrong segment alignment: " + sampaLabel + " != " + sp.getHTSModel().getPhoneName();
				segIU = new SysSegmentIU(l, sp.getHTSModel(), sp.getFeatureVector(), sp.getHMMData(), null);
			} else { // the standard case: no HMM synthesis with this segment
				segIU = new SysSegmentIU(l);
			}
			return segIU;
		}
	}
	
	public static CharSequence joinList(List<? extends Object> list, CharSequence connector) {
		StringBuilder sb = new StringBuilder();
		for (Iterator<? extends Object> iter = list.iterator(); iter.hasNext();) {
			sb.append(iter.next().toString());
			if (iter.hasNext())
				sb.append(connector);
		}
		return sb;
	}
	
}
