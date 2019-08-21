package inpro.incremental;

import inpro.incremental.unit.EditMessage;
import inpro.incremental.unit.IU;

import java.util.Collection;
import java.util.List;

/**
 * Defines the interface for receiving hypothesis changes 
 * of incremental processors.
 * 
 * Communication in the inpro system follows an event-based
 * <em>push</em>-scheme. Each event comprises the communicated 
 * information in two forms:
 * <ul>
 * <li>a list of all IUs in the current hypothesis, and</li>
 * <li>a list of all edits to above list between this and the previous call
 * to the method.</li>
 * </ul>
 * 
 * In this way, processors can choose whether they rather base their
 * calculations on the complete information, or on the edits. E.g.,
 * a processor that want to listen for the occurrence of a specific IU
 * can limit its analysis to add-messages with the IU in question as payload;
 * in contrast, a processor that is interested in the ultimate IU of the
 * current hypothesis will rather just inspect the last element of the IU list.
 * 
 * Notice that full-fledged incremental modules (which also generate incremental
 * output) should rather subclass {@link IUModule} which handles output handling
 * and offers some more convenience operations.
 * 
 * @author timo
 */
public abstract class PushBuffer {

	/**
	 * this should receive a list of current IUs and 
	 * a list of edit messages since the last call to hypChange
	 * 
	 * @param ius while this is a (plain) collection, the collection's iterator()
	 *        method must ensure a sensible ordering of the returned elements.
	 *        For now we have only used Lists (which are ordered), 
	 *        but a Tree of IUs should also be possible and this should gracefully
	 *        work together with processors that expect lists 
	 * @param edits a list of edits since the last call to hypChange
	 */
	public abstract void hypChange(Collection<? extends IU> ius, List<? extends EditMessage<? extends IU>> edits);

}
