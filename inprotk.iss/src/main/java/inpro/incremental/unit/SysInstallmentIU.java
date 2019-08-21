package inpro.incremental.unit;

import inpro.annotation.Label;
import inpro.audio.AudioUtils;
import inpro.synthesis.MaryAdapter;
import inpro.synthesis.MaryAdapter5internal;
import inpro.synthesis.hts.FullPFeatureFrame;
import inpro.synthesis.hts.IUBasedFullPStream;
import inpro.synthesis.hts.VocodingAudioStream;
import org.apache.log4j.Logger;

import javax.sound.sampled.AudioInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * an installment that supports incremental synthesis
 * @author timo
 */
public class SysInstallmentIU extends InstallmentIU {
	
	Logger logger = Logger.getLogger(this.getClass());
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SysInstallmentIU(String tts) {
		super(null, tts);
		groundedIn = (List) MaryAdapter.getInstance().text2WordIUs(tts);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" }) // allow cast from List<WordIU> to List<IU>
	public SysInstallmentIU(String tts, List<WordIU> words) {
		super(null, tts);
		groundedIn = (List) words;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" }) // allow cast from List<WordIU> to List<IU>
	public SysInstallmentIU(List<? extends IU> words) {
		super(null, "");
		groundedIn = (List) words;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void scaleDeepCopyAndStartAtZero(double scale) {
		List<WordIU> newWords = new ArrayList<WordIU>();
		WordIU prevWord = null;
		double startTime = startTime();
		List<WordIU> words = (List<WordIU>) groundedIn();
		for (WordIU w : words) {
			List<SysSegmentIU> newSegments = new ArrayList<SysSegmentIU>();
			for (SegmentIU seg : w.getSegments()) {
				// TODO: the following needs to be reworked to restart from a newly generated feature vector
				newSegments.add(new SysSegmentIU(new Label(
						(seg.l.getStart() - startTime) * scale, 
						(seg.l.getEnd() - startTime) * scale, 
						seg.l.getLabel()
				), seg instanceof SysSegmentIU ? ((SysSegmentIU) seg).legacyHTSmodel : null,
				   seg instanceof SysSegmentIU ? ((SysSegmentIU) seg).fv : null,
			       seg instanceof SysSegmentIU ? ((SysSegmentIU) seg).hmmdata : null,
				   seg instanceof SysSegmentIU ? ((SysSegmentIU) seg).hmmSynthesisFeatures : Collections.<FullPFeatureFrame>emptyList()));
			}
			// connect same-level-links
			Iterator<SysSegmentIU> it = newSegments.iterator();
			SysSegmentIU first = it.next();
			while (it.hasNext()) {
				SysSegmentIU next = it.next();
				next.setSameLevelLink(first);
				first = next;
			}
			WordIU newWord = new WordIU(w.getWord(), prevWord, (List) newSegments);
			newWords.add(newWord);
			prevWord = newWord;
		}
		groundedIn = (List) newWords;
	}
	
	/**
	 * return the HMM parameter set for this utterance (based on getWords())
	 */
	public IUBasedFullPStream getFullPStream() {
		return new IUBasedFullPStream(getInitialWord());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<SysSegmentIU> getSegments() {
		List<SysSegmentIU> segments = new ArrayList<SysSegmentIU>();
		for (WordIU word : getWords()) {
			segments.addAll((List) word.getSegments());
		}
		return segments;
	}
	
	public CharSequence toLabelLines() {
		StringBuilder sb = new StringBuilder();
		for (SysSegmentIU seg : getSegments()) {
			sb.append(seg.toLabelLine());
			sb.append("\n");
		}
		return sb;
	}
	
	public AudioInputStream getAudio() {
        boolean immediateReturn = true;
		VocodingAudioStream vas = new VocodingAudioStream(getFullPStream(), MaryAdapter5internal.getDefaultHMMData(), immediateReturn);
        return AudioUtils.get16kAudioStreamForVocodingStream(vas);
	}
	
	public String toMbrola() {
		StringBuilder sb = new StringBuilder();
		for (WordIU word : getWords()) {
			sb.append(word.toMbrolaLines());
		}
		sb.append("#\n");
		return sb.toString();
	}
	
	public WordIU getInitialWord() {
		return (WordIU) groundedIn.get(0);
	}
	
	public WordIU getFinalWord() {
		List<WordIU> words = getWords();
		return words.get(words.size() - 1);
	}
	
	public List<WordIU> getWords() {
		List<WordIU> activeWords = new ArrayList<WordIU>();
		WordIU word = getInitialWord();
		while (word != null) {
			activeWords.add(word);
			word = (WordIU) word.getNextSameLevelLink();
		}
		return activeWords;
	}
	
	@Override
	public String toPayLoad() {
		StringBuilder sb = new StringBuilder();
		for (WordIU word : getWords()) {
			sb.append(word.toPayLoad());
			sb.append(" ");
		}
		return sb.toString();
	}
	
	public String toMarkedUpString() {
		StringBuilder sb = new StringBuilder();
		for (WordIU word : getWords()) {
			String payload = word.toPayLoad().replace(">", "&gt;").replace("<", "&lt;");
			if (word.isCompleted()) {
				sb.append("<strong>");
				sb.append(payload);
				sb.append("</strong>");
			} else if (word.isOngoing()) {
				sb.append("<em>");
				sb.append(payload);
				sb.append("</em>");
			} else
				sb.append(payload);
			sb.append(" ");
		}
		return sb.toString();
	}
	
}
