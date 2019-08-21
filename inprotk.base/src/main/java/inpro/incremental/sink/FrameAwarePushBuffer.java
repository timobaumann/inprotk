package inpro.incremental.sink;

import inpro.incremental.FrameAware;
import inpro.incremental.PushBuffer;

public abstract class FrameAwarePushBuffer extends PushBuffer implements FrameAware {

	int currentFrame = 0;

	@Override
	public void setCurrentFrame(int frame) {
		currentFrame = frame;
	}
	
}
