package inpro.audio;

import inpro.util.PathUtil;
import org.apache.log4j.Logger;

import javax.sound.sampled.*;
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
		streamQueue.add(is);
		synchronized(this) {
			notifyAll();
		}
	}

	/** clears any ongoing stream, as well as any queued streams */
	public void clearStream() {
		setStream(null);
		synchronized(this) {
			notifyAll();
		}
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
			synchronized(this) {
				notifyAll();
			}
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
		synchronized(this) {
			notifyAll();
		}
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

	public static DispatchStream drainingDispatchStream() {
		DispatchStream dispatcher = new DispatchStream();
		try {
			AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 16000.f, 16, 1, (16/8)*1, 16000.f, false);
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
			if (!AudioSystem.isLineSupported(info)) {
				throw new RuntimeException("Line matching " + info + " not supported.");
			}
			final SourceDataLine line;
			line = (SourceDataLine) AudioSystem	.getLine(info);
			line.open(format, 1280);
			System.err.println("speaker actually has buffer size " + line.getBufferSize());


			Runnable streamDrainer = () -> {
				byte[] b = new byte[320]; // that will fit 10 ms
				while (true) {
					int bytesRead = 0;
					try {
						bytesRead = dispatcher.read(b, 0, b.length);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					if (bytesRead > 0)
						// no need to sleep, because the call to the microphone will already slow us down
						line.write(b, 0, bytesRead);
					else {// if there is no data, then we wait a little for data to become available (instead of looping like crazy)
						if (bytesRead <= 0 && dispatcher.inShutdown())
							return;
						try {
							Thread.sleep(20);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			};

			new Thread(streamDrainer, "streamToSpeakers").start();
			line.start();
		} catch (LineUnavailableException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		return dispatcher;
	}
	
}
