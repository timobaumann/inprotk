package inpro.incremental.unit;

public enum EditType {
	ADD, REVOKE, COMMIT; //, SUBSTITUTE // this one does not play well with the other classes (yet?)
	// how about a generic UPDATE -> that could subsume different confidences and even commit
	// this could be extended by adding ASSERT90, ASSERT95, ASSERT98, ...
	// which would then signify the likelihood in percent
	
	public boolean isCommit() {
		return this.equals(COMMIT);
	}
	
}
