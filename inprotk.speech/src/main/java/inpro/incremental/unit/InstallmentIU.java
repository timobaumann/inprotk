package inpro.incremental.unit;

import java.util.Collections;
import java.util.List;

/**
 * A simple wrapper for both system and user uttered installments
 * - for user installments references to recognized WordIUs are kept 
 *   used to infer the spoken text in toPayload()
 * - for system installments a reference to the corresponding dialogueAct
 *   is kept and the spoken text is stored in the variable @see{tts}.
 * @author timo
 */
public class InstallmentIU extends IU {

	/**
	 * String holding the system reponse
	 */
	protected String tts;
	/**
	 * Variable specifying who produced this installment
	 */
	boolean systemProduced; // true: system utterance, false: user utterance

	/**
	 * Constructor for an installment produced by the user, grounded
	 * in a list of spoken words.
	 * @param currentInstallment the list of wordIUs grounding this installment
	 */
	public InstallmentIU(List<WordIU> currentInstallment) {
		super(currentInstallment);
		this.systemProduced = false;
		this.tts = null;
	}

	/**
	 * Constructor for an installment produced by the system, grounded
	 * in a dialogueAct.
	 * @param dialogueAct the dialogue act grounding this installment
	 * @param tts the spoken string
	 */
	public InstallmentIU(IU dialogueAct, String tts) {
		super(dialogueAct != null ? Collections.<IU>singletonList(dialogueAct) : Collections.<IU>emptyList());
		this.systemProduced = true;
		this.tts = tts;
	}

	/**
	 * Determines whether the installment is a 'system' one.
	 * @return true if produced by the system, false if by the user.
	 */
	public boolean systemProduced() {
		return systemProduced;
	}
	
	/**
	 * Determines whether the installment is a 'user' one.
	 * @return true if produced by the user, false if by the system.
	 */
	public boolean userProduced() {
		return !systemProduced;
	}

	/**
	 * Returns a string representation of the payload of this IU.
	 * For user-produced installments, the payload is a String representation
	 * of the spoken words grounding this IU.
	 * For system-produced installments, it's a string representation of
	 * the spoken output grounded in this IU.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public String toPayLoad() {
		if (systemProduced) {
			return tts;
		} else { // user produced
			List<WordIU> words = (List<WordIU>) groundedIn();
			StringBuilder text = new StringBuilder(WordIU.wordsToString(words));
			if (!words.isEmpty()) { 
				WordIU lastWord = words.get(words.size() - 1);
				while (lastWord != null && lastWord.isSilence()) {
					lastWord = (WordIU) lastWord.previousSameLevelLink;
				}
			}
			return text.toString();
		}
	}

}
