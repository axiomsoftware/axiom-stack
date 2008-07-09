package axiom.scripting.rhino.debug;

class DebugMonitor {
	boolean isStepping = false;
	boolean isJumping = false;
	
	public boolean isStepping() {
		boolean isStepping = this.isStepping;
		this.isStepping = false;
		return isStepping;
	}
	
	public boolean isJumping() {
		boolean isJumping = this.isJumping;
		this.isJumping = false;
		return isJumping;
	}
}