package inpro.incremental.unit;

public class EditMessage<IUType extends IU> {

	private final EditType type;
	private final IUType iu;
	
	public EditMessage(EditType edit, IUType iu) {
		this.type = edit;
		this.iu = iu;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(type.toString());
		sb.append("(");
		sb.append(iu.toString());
		sb.append(")");
		return sb.toString();
	}
	
	/**
	 * equality for EditMessages is defined by the contained IUs being equal
	 * and the EditType being the same
	 */
	@Override
	public boolean equals(Object o) {
		if (o instanceof EditMessage) {
			@SuppressWarnings("unchecked")
			EditMessage<? extends IU> edit = (EditMessage<? extends IU>) o;
			return (this.type == edit.type) && (this.iu.equals(edit.iu));
		} else 
			return false;
	}

	public IUType getIU() {
		return iu;
	}
	
	public EditType getType() {
		return type;
	}

	@Override
	public int hashCode() {
		return type.hashCode() + iu.hashCode();
	}
	
}
