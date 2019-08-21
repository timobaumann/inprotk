package inpro.incremental.source;

import inpro.incremental.FrameAware;
import inpro.incremental.PushBuffer;
import inpro.incremental.unit.EditMessage;
import inpro.incremental.unit.EditType;
import inpro.incremental.unit.IUList;
import inpro.incremental.unit.TextualWordIU;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.util.ArrayList;
import java.util.List;


/**
 * An IUDocument stores a list of current IUs, 
 * edits since the last update and
 * the string for the (partial) next IU
 * 
 * It handles HypothesisChangeListeners 
 * which are notified, when the IUList changes (or is committed)
 * (and can be set via setListeners())
 * 
 * IUs are committed and the list is reset after an explicit call to commit()
 * (when used as the document of a JTextField, this can be done by 
 *  calling commit() from a JTextField's ActionListener)
 *  
 * @see javax.swing.text.Document Document
 * @author timo
 */
@SuppressWarnings("serial")
public class IUDocument extends PlainDocument {

	List<PushBuffer> listeners = new ArrayList<PushBuffer>();
	IUList<TextualWordIU> wordIUs = new IUList<TextualWordIU>();
	List<EditMessage<TextualWordIU>> edits = new ArrayList<EditMessage<TextualWordIU>>();
	String currentWord = "";
	
	int currentFrame = 0;
	
	public void setListeners(List<PushBuffer> listeners) {
		this.listeners = listeners;
	}
	
	public void addListener(PushBuffer listener) {
		this.listeners.add(listener);
	}
	
	public void notifyListeners() {
		if (edits != null && !edits.isEmpty()) {
			//logger.debug("notifying about" + edits);
			currentFrame += 100;
			for (PushBuffer listener : listeners) {
				if (listener instanceof FrameAware) {
					((FrameAware) listener).setCurrentFrame(currentFrame);
				}
				// notify
				if (wordIUs != null)
					listener.hypChange(wordIUs, edits);
				
			}
			edits = new ArrayList<EditMessage<TextualWordIU>>();
		}
	}
	
	private void addCurrentWord() {
		//logger.debug("adding " + currentWord);
		TextualWordIU sll = (wordIUs.size() > 0) ? wordIUs.get(wordIUs.size() - 1) : TextualWordIU.FIRST_ATOMIC_WORD_IU;
		TextualWordIU iu = new TextualWordIU(currentWord, sll);
		EditMessage<TextualWordIU> edit = new EditMessage<TextualWordIU>(EditType.ADD, iu);
		edits.add(edit);
		wordIUs.add(iu);
		//logger.debug(edit.toString());
		currentWord = "";
	}
	
	public void commit() {
		// handle last word (if there is one)
		if (!"".equals(currentWord)) {
			addCurrentWord();
		}
		// add commit messages
		for (TextualWordIU iu : wordIUs) {
			edits.add(new EditMessage<TextualWordIU>(EditType.COMMIT, iu));
			iu.commit();
//			iu.update(EditType.COMMIT);
		}
		// notify
		notifyListeners();
		// reset
		try {
			super.remove(0,getLength());
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
		wordIUs = new IUList<TextualWordIU>();
		edits = new ArrayList<EditMessage<TextualWordIU>>();
	}
	
	/* Overrides over PlainDocument: */
	
	/** 
	 * only allow removal at the right end
	 * and correctly handle removals beyond the current word
	 */
	@Override
	public void remove(int offs, int len) throws BadLocationException {
		if (offs + len == getLength()) { // only allow removal at the right end
			super.remove(offs, len);
//				if (getText(getLength() - 1, 1).)
			while (len > currentWord.length()) { // +1 because the whitespace has to be accounted for
				len -= currentWord.length();
				len--; // to account for whitespace
				TextualWordIU iu = wordIUs.remove(wordIUs.size() - 1);
				EditMessage<TextualWordIU> edit = new EditMessage<TextualWordIU>(EditType.REVOKE, iu);
				edits.add(edit);
				//logger.debug(edit.toString());
				currentWord = iu.getWord();
			}
			currentWord = currentWord.substring(0, currentWord.length() - len);
			//logger.debug("now it's " + currentWord);
			notifyListeners();
		}
	}
	
	/* character by character: 
	 *    check validity (for example, we don't like multiple whitespace)
	 *    add them to the current word, if it's not whitespace
	 *    add current word to IUList if it is whitespace
	 * finally, add the (possibly changed characters) to the superclass's data handling
	 */
	public void addChar(char ch) {
		if (Character.isWhitespace(ch)) {
			if (currentWord.length() > 0) {
				addCurrentWord();
			} else {
				//logger.debug("ignoring additional whitespace");
			}
		} else {
			//logger.debug("appending to currentWord");
			currentWord += ch;
			//logger.debug("now it's " + currentWord);
		}
	}

	/** 
	 * only allow insertion at the right end
	 */
	@Override
	public void insertString(int offs, String str, AttributeSet a)
			throws BadLocationException {
		if (offs == getLength()) {
			char[] chars = str.toCharArray();
			for (char ch : chars) {
				addChar(ch);
			}
			super.insertString(offs, str, a);
		}
		notifyListeners();
	}
	
}
	
