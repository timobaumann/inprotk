package inpro.incremental.unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

@SuppressWarnings("serial")
public class IUList<IUType extends IU> extends ArrayList<IUType> {

	private static final Logger logger = Logger.getLogger(IUList.class);

	IUType firstIU;
	
	public IUList() {
		super();
	}
	
	public IUList(List<IUType> base) {
		super(base);
		if (!base.isEmpty())
			firstIU = base.get(0);
	}
	
 	/** constructor to create a list with a certain default size) */
	public IUList(int i) { super(i); }

	public void apply(EditMessage<IUType> edit) {
 		switch (edit.getType()) {
 			case ADD: 
 				this.add(edit.getIU(), false);
 				break;
 			case REVOKE: // assertion errors on REVOKE seem to only happen as a consequence of earlier errors on ADD
 				assert !isEmpty() : "Can't revoke from an empty list: " + edit;
 				assert (!edit.getIU().isCommitted()) : "you're trying to revoke an IU that was previously committed.";
 				//edit.getIU().revoke();
 				this.remove(size() - 1);
				break;
 			case COMMIT:
 				edit.getIU().commit();
 				break;
 			default:
 				throw new RuntimeException("If you implement new EditTypes, you should also implement their handling!");
 		}
 	}
 	
 	public void apply(List<EditMessage<IUType>> edits) {
 		try {
 			if (edits != null) {
				for (EditMessage<IUType> edit : edits) {
					apply(edit);
				}
 			}
	 	} catch (AssertionError ae) {
 			logger.fatal("this list contains: ");
 			logger.fatal(this);
 			logger.fatal("details of list: ");
 			for (IUType iu : this) {
 				logger.fatal(iu.deepToString());
 			}
 			logger.fatal("list of edits given was " + edits);
 			logger.fatal("details of list of edits: ");
 			for (EditMessage<IUType> edit : edits) {
 				logger.fatal(edit.getIU().deepToString());
 			}
 			logger.fatal(ae);
 			throw ae;
 		}
	}
 	
 	private List<EditMessage<IUType>> calculateDiff(List<IUType> other, boolean relaxedEquality) {
 		Iterator<IUType> thisIt = iterator();
 		Iterator<IUType> otherIt = other.iterator();
 		
 		List<EditMessage<IUType>> addEdits = new ArrayList<EditMessage<IUType>>();
 		List<EditMessage<IUType>> revokeEdits = new ArrayList<EditMessage<IUType>>();
 		List<EditMessage<IUType>> commitEdits = new ArrayList<EditMessage<IUType>>();
 		
 		// check the prefix of both lists
 		while (thisIt.hasNext() && otherIt.hasNext()) {
 			IUType thisElem = thisIt.next();
 			IUType otherElem = otherIt.next();
 			if (!(thisElem.equals(otherElem) || 
 				 (relaxedEquality && thisElem.payloadEquals(otherElem))
 			   )) {
 				// handle the first no-match
 				revokeEdits.add(new EditMessage<IUType>(EditType.REVOKE, thisElem));
 				addEdits.add(new EditMessage<IUType>(EditType.ADD, otherElem));
 				break;
 			} else if (!thisElem.isCommitted() && otherElem.isCommitted() ) {
 				// handle commits (which should occur only in the prefix)
 				commitEdits.add(new EditMessage<IUType>(EditType.COMMIT, otherElem));
 			}
 		}
 		// now create revokes for remaining IUs in thisIt
 		while (thisIt.hasNext())
 			revokeEdits.add(new EditMessage<IUType>(EditType.REVOKE, thisIt.next()));
 		// now add remaining IUs from otherIt
 		while (otherIt.hasNext())
 			addEdits.add(new EditMessage<IUType>(EditType.ADD, otherIt.next()));

 		// now construct the final list
 		Collections.reverse(revokeEdits); // revokes have to be ordered from right to left
 		List<EditMessage<IUType>> edits = revokeEdits; // we can just keep the list instead of creating a new one and addAlling the revokes
 		edits.addAll(addEdits);

 		return edits;
 	}
 	
 	/** 
 	 * Calculate the difference (in edits) between an other and this list.
 	 * 
 	 * return the edits that are necessary to turn this list into the other list.
 	 * 
 	 * the following holds: 
 	 * other.equals(this.apply(this.diff(other)));
 	 * (except that apply does not return a value but changes its argument...)
 	 * 
 	 * @param other the reference list
 	 * @return returns the list of edits that have to be applied to this
 	 */
 	public List<EditMessage<IUType>> diff(List<IUType> other) {
 		return calculateDiff(other, false);
 	}
 	
 	/**
 	 * similar in spirit to diff, this method allows differences 
 	 * in IU identity, but not in IU payload
 	 */
 	public List<EditMessage<IUType>> diffByPayload(List<IUType> other) {
 		return calculateDiff(other, true);
 	}
 	
 	/**
 	 * adds an element to the list, optionally deep-connecting same-level links
 	 * same-level links are set for temporally sorted lists, but not changed for 
 	 * unsorted lists (alternative hypotheses as in parsing)
 	 * @param e the element to add to the list
 	 * @param deepSLL determines whether same-level links should be set only
 	 * on the element, or also on unconnected IUs in the grounded-in hierarchy.
 	 */
 	public void add(IUType e, boolean deepSLL) {
 		if (deepSLL) {
 			e.connectSLL(getLast());
 		}
 		add(e);
 	}

	public IUType getLast() {
		if (isEmpty()) 
			return null;
		else
			return get(size() - 1);
	}
	
 	@Override
 	public void clear() {
 		super.clear();
 		if (firstIU != null)
 			add(firstIU);
 	}

 	/**
 	 * connect the IUs in the list via their SLLs.
 	 * any older SLL asignment will be overwritten
 	 */
	public void connectSLLs() {
		IUType prev = null;
		Iterator<IUType> iuIt = iterator();
		while (iuIt.hasNext()) {
			IUType iu = iuIt.next();
			if (iu.getSameLevelLink() == null)
				iu.connectSLL(prev);
			prev = iu;
		}
	}
	
}
