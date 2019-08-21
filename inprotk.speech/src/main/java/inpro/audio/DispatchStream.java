package inpro.audio;

import inpro.util.PathUtil;
import org.apache.log4j.Logger;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * WARNING: there ARE threading issues with this class
 * 
 * TODO: would it be nice to directly output SysInstallmentIUs? --> yes, indeed.
 * @author timo
 */
public class DispatchStream extends InputStream {

	private static Logger logger = Logger.getLogger(DispatchStream.class);

	private boolean sendSilence = true;
	
	InputStream stream;
	final Queue<InputStream> streamQueue = new ConcurrentLinkedQueue<InputStream>();


	public void initialize() {  }

	public boolean isSpeaking() {
		return stream != null;
	}
	
	/**
	 * determines whether digital zeroes are sent during silence,
	 * or whether the stream just stalls 
	 */
	public void sendSilence(boolean b) {
		sendSilence = b;
	}

	public void playStream(InputStream audioStream) {
		playStream(audioStream, false);
	}

	/* * Higher-level audio enqueuing (or direct play) * */
	/**
	 * play audio from file
	 * @param filename path to the file to be played
	 * @param skipQueue determines whether the file should be played
	 *        immediately (skipQueue==true) or be enqueued to be played
	 *        after all other messages have been played
	 */
	public void playFile(String filename, boolean skipQueue) {
		if (skipQueue)
			logger.info("Now playing file " + filename);
		else
			logger.info("Now appending file " + filename);
		AudioInputStream audioStream;
		try {
			audioStream = AudioSystem.getAudioInputStream(PathUtil.anyToURL(filename));
		} catch (Exception e) {
			logger.error("can't play file " + filename);
			audioStream = null;
			e.printStackTrace();
		}
		playStream(audioStream, skipQueue);
	}

	public void playStream(InputStream audioStream, boolean skipQueue) {
		if (skipQueue)
			setStream(audioStream);
		else
			addStream(audioStream);
	}
	
	/* * Stream and Stream Queue handling * */
	
	protected void addStream(InputStream is) {
		logger.info("adding stream to queue: " + is);
		//synchronized(this) {
//			if (stream != null) {
				//if (streamQueue.isEmpty())
					streamQueue.add(is);
//			} else {
//				stream = is;
//			}
		//}
	}

	/** clears any ongoing stream, as well as any queued streams */
	public void clearStream() {
		setStream(null);
	}
	
	protected void setStream(InputStream is) {
		logger.debug("playing a new stream " + is);
		synchronized(this) {
			streamQueue.clear();
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			stream = is;
		}
	}
	
	/* * InputStream implementation * */
	
	@Override
	public int read() throws IOException {
		throw new RuntimeException();
/*		synchronized(this) {
			if (stream == null)  {
				stream = streamQueue.poll();
			}
			int returnValue = (stream != null) ? stream.read() : 0;
			if (returnValue == -1) {
				stream.close();
				stream = null;
				returnValue = 0;
			}
			return returnValue;
		} */
	}
	
	private void nextStream() {
		stream = streamQueue.poll();
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int bytesRead = 0;
		synchronized(this) {
			if (stream == null) {
				nextStream();
			}
			while (stream != null) {
				bytesRead = stream.read(b, off, len);
				if (bytesRead == -1) {
					nextStream();
				} else break;
			}
			if (bytesRead < len) { // if the stream could not provide enough bytes, then it's probably ended
				if (sendSilence && !inShutdown) { 
					if (bytesRead < 0) 
						bytesRead = 0;
// for silence:
					Arrays.fill(b, off + bytesRead, off + len, (byte) 0);
// for low noise:
//					for (int i = off; i < off + len; i++) {
//						b[i] = (byte) randomNoiseSource.nextInt(2);				
//					}
					bytesRead = len;
				}
				if (stream != null) {
					stream.close();
					stream = null;
				}
			}
		}
		return bytesRead;
	}
	
	private boolean inShutdown = false;
	/** whether this dispatchStream has been requested to shut down */ 
	public boolean inShutdown() { return inShutdown; }
	/** waits for the current stream to finish and shuts down the dispatcher */
	public void shutdown() { inShutdown = true; }

	public void waitUntilDone() {
		synchronized(this) {
			while (isSpeaking()) {
				try {
					wait();	} catch (InterruptedException e) { e.printStackTrace();
				}
			}
		}
	}

	
}
