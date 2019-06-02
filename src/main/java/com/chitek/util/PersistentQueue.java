/*******************************************************************************
 * Copyright 2012-2013 C. Hiesserich
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.chitek.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.log4j.Logger;

/** 
 * A concurrent persistent queue that is backed by a disk file. The queue uses a LinkedList internally.
 * <P>
 * The remove(Object), removeAll and retainAll are potentially unsafe when a crash happens during this operation.
 * Also, this methods trigger a complete rewrite of the queue file. It is not recommended to use them.
 * <P>
 * <i>Defragmentation</i>: When the first element of the queue is deleted, not
 * the entire file is written. Instead, a <code>QueueMarker</code>
 * is appended to the end of the file to signal that the first element has been
 * deleted. However, after some number of remove operations (by default, this is 50,
 * but that can be changed via an optional parameter at instantiation), the entire
 * file is defragmented: a temporary file is written with all contents of the
 * queue. It is then renamed to match the name of the original file. The name of
 * the temporary file is the original filename plus '.temp'.
 */
public class PersistentQueue<E extends Serializable> implements Queue<E> {

	private static final String TEMPFILE_EXTENSION = ".tmp";
	private static final int DEFAULT_DEFRAGMENT_INTERVAL = 50;
	private final int contentHash;
	private Integer fileHash = null;	// The currently active hash value in the queue file
	private final String filename;
	protected final Logger log;
	private LinkedList<E> list;
	private int OperationsSinceDefragment = 0;
	private boolean usePersistance;
	private int defragmentInterval;
	
	private final File queueFile;
	private ObjectOutputStream objectOutput = null;
	
	/**
	 * Create a persitent queue. If the file is already present, the queue is initalized from file.
	 * 
	 * @param filename
	 * 	The filename to use as persitance storage
	 * @param contentHash
	 * 	A hash value that is used when loading the queue from an existing file. If the hash value stored in the file
	 *  does not match the given contentHash, the stored entrys are discarded.
	 * @param usePersistance
	 *  If true, all queue operations will be written to a file immediately. If false, the queue will be saved only if
	 *  {@link #close()} is called.
	 * @param logger
	 *  If not null, this class will create a sub logger of the given logger.
	 * @throws IOException if access to the queue file fails.
	 */
	public PersistentQueue(String filename, int contentHash, boolean usePersistance, Logger log) throws IOException {
		if (log !=null)
			this.log = Logger.getLogger(String.format("%s.%s", log.getName(), PersistentQueue.class.getSimpleName()));
		else 
			this.log = Logger.getLogger(PersistentQueue.class.getSimpleName());
		
		this.defragmentInterval = DEFAULT_DEFRAGMENT_INTERVAL;
		
		this.contentHash = contentHash;
		this.filename = filename;
		this.usePersistance = usePersistance;
		list = new LinkedList<E>();
                
        // Read queue if file exists or create a new file
		queueFile = new File(filename);
        if (queueFile.exists()) {
            readQueueFromFile();
        } else {
        	rewriteFile();
        }
          
	}
	
	/**
	 * Closes the OutputStream. Should be called when this queue is not used any more.
	 * Simply setting  the PersitentQueue = null will leave the file opened!
	 */
	public synchronized void close() {
		if (!usePersistance)
			rewriteFile();
		
		if (objectOutput != null)
			try {
				log.debug("Closing ObjectOutputStream");
				objectOutput.close();
			} catch (Exception e) {}
		objectOutput = null;
	}
		
	@Override
	public boolean addAll(Collection<? extends E> c) {
		boolean result = list.addAll(c);
		
		// The queue file is rewritten. This is just laziness, a good implementation should check the count
		// of items to add and append to the file, if this count is smaller than the current list size.
		if (result && usePersistance)
			rewriteFile();
		
		return result;
	}

    /**
     * Clears the entire queue and forces the underlying file to be rewritten.
     * @throws IOException if an I/O error occurs
     */
    public synchronized void clear() {
        list.clear();

		if (usePersistance) {
			try {
				// Add a marker to the current queue file in case rewrite fails
				appendToFile(QueueMarker.Clear);
				// Create a new empty queue file
				rewriteFile();

			} catch (Exception e) {
				log.error(String.format("Exception clearing the persitent queue file: %s", e.toString()));
			}
		}
    }

	@Override
	public boolean contains(Object o) {
		return list.contains(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return list.containsAll(c);
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty();
	}

	@Override
	public Iterator<E> iterator() {
		return list.iterator();
	}

	@Override
	public boolean remove(Object o) {
		boolean result = list.remove(o);
		if (result && usePersistance) rewriteFile();
		return result;		
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean result = list.removeAll(c);
		if (result && usePersistance) rewriteFile();
		return result;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		boolean result = list.retainAll(c);
		if (result && usePersistance) rewriteFile();
		return result;
	}

	@Override
	public int size() {
		return list.size();
	}

	@Override
	public Object[] toArray() {
		return list.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return list.toArray(a);
	}

	@Override
	public boolean add(E e) {
		
		boolean result = list.add(e);
		if (result && usePersistance)
			appendToFile(e);

		return result;
	}

	@Override
	public E element() {
		return list.element();
	}

	@Override
	public boolean offer(E e) {
		boolean result = list.offer(e);
		if (result && usePersistance)
			appendToFile(e);
		return result;
	}

	@Override
	public E peek() {
		return list.peek();
	}

    /**
     * Retrieves, but does not remove, the last element of the queue,
     * or returns <tt>null</tt> if this deque is empty.
     *
     * @return the tail of this queue, or <tt>null</tt> if this queue is empty
     */
    public E peekLast() {
    	return list.peekLast();
    }
	
    /**
     * Returns the element at the specified position in this queue.
     * 
     * @param index
     * @return
     * @throws IndexOutOfBoundsException if the index is out of range (index < 0 || index >= size())
     */
    public E get(int index) {
    	return list.get(index);
    }
    
	@Override
	public E poll() {
		E entry = list.poll();
		
		if (entry != null && usePersistance)
			removeFromFile();
        
		return entry;
	}

	@Override
	public E remove() {
        E entry = list.remove(); // remove() throws an Exception is list is empty
		if (entry != null && usePersistance)
			removeFromFile();
		
        return entry;
	}
	
	@SuppressWarnings("unchecked")
	private synchronized void readQueueFromFile() {
		ObjectInputStream ois = null;
		
		// Entrys are invalid until a matching hash is found
		fileHash = null;
		int discarded = 0;
		
		try {
			InputStream fis = Files.newInputStream(queueFile.toPath());
			ois = new ObjectInputStream(fis);

			try {
				Serializable entry = null;
				while ((entry = (Serializable) ois.readObject()) != null) {
					if (log.isTraceEnabled())
						log.trace(String.format("Read item %s", 
							entry.getClass().getSimpleName()) + (entry instanceof QueueMarker?"-"+entry.toString():""));
					if (entry instanceof QueueMarker)
						// QueueMarker is added to the file when a entry is removed
						switch ((QueueMarker) entry) {
						case Remove:
							if (!list.isEmpty())
								list.removeFirst();
							break;
						case Clear:
							list.clear();
							break;
						}
					else if (entry instanceof Integer) {
						 // Any Integer is a new contentHash
						fileHash = (Integer)entry;
					} else {
						// Normal queue entry
						if (fileHash != null && fileHash.equals(contentHash)) {
							// Only add to queue if content hash matches
							try {
								list.add((E) entry);
							} catch (ClassCastException e) {
								log.warn(String.format("Error loading persitent queue entry from file %s: %s",
									filename, e.toString()));
							}
						} else {
							discarded ++;
						}
					} 
				}
			} catch (ClassNotFoundException e) {
				log.warn(String.format("Error loading persitent queue entry from file %s: %s", filename, e.toString()));
			} catch (EOFException eof) {}

		} catch (Exception e) {
			log.error(String.format("Exception loading persistent queue from file %s: %s", filename, e.toString()));
			if (log.isDebugEnabled())
				log.debug("StackTrace:", e);
		} finally {
			try {
				if (ois != null)
					ois.close();
			} catch (Exception e) {	}
		}
		
		if (usePersistance)
			rewriteFile();
		else
			deleteQueueFile();
        
		if (log.isDebugEnabled())
			log.debug(String.format("Loaded %d entrys from file %s. %d invalid entrys have been discarded.",
				list.size(), queueFile.getAbsolutePath(), discarded));
	}
		
    /** Appends an entry to the queue file. If the append fails, this method will try to rewrite the file.*/
    private synchronized void appendToFile(Serializable entry) { 	
        if (!usePersistance) return;
        
        try {
        	objectOutput.writeObject(entry);
        	objectOutput.reset();
        	objectOutput.flush();
		} catch (Exception e) {
			// If append fails, try to rewrite the file
			log.warn(String.format("IOException appending object to persitent queue file %s: %s", filename, e.toString()));
			rewriteFile();
		} 
    }
    
    /** Add a removed marker to the queue file */
	private synchronized void removeFromFile() {
		if (!usePersistance) return;
		
		// Append marker to file. If the count of operations exceed the limit, the file is rewritten
		OperationsSinceDefragment++;
		if (OperationsSinceDefragment >= defragmentInterval) {
			rewriteFile();
		} else {
			appendToFile(QueueMarker.Remove);
		}
	}
    
    /** Writes the list to a new file and deletes the existing queue file.*/
    private synchronized void rewriteFile() {
        String tempFilename = filename + TEMPFILE_EXTENSION;
               
        // write the temporary file
        if(log.isDebugEnabled())
        	log.debug(String.format("Writing temporary queue file %s", tempFilename));
        File tempFile = new File(tempFilename);
		ObjectOutputStream oos = null;
		try {
			OutputStream fos = Files.newOutputStream(tempFile.toPath());
			OutputStream buffer = new BufferedOutputStream(fos);
			oos = new ObjectOutputStream(buffer);
			oos.writeObject(Integer.valueOf(contentHash));
			if (!list.isEmpty()) {
				for (Serializable entry : list) {
					oos.writeObject(entry);
				}
			}

		} catch (IOException e1) {
			log.error(String.format("Exception rewriting persistent queue file: %s", e1.toString()));
			return;
		} finally {
			try {
				if (oos != null) oos.close();
			} catch (Exception e) {	}
		}

        // Delete the old queue file and rename the temp file
		deleteQueueFile();
        if (!tempFile.renameTo(queueFile)) {
        	// Old file could not be replaced, maybe there is still an instance active
        	// Continue using the old file
            log.error(String.format("Unable to rename temporary queue file %s to %s", tempFilename, filename));
            tempFile.delete();
        } else {
        	// Everything ok, file hash has been written
        	fileHash=Integer.valueOf(contentHash);
        }
             
        try {
        	OutputStream  fos = Files.newOutputStream(queueFile.toPath(), StandardOpenOption.APPEND);
        	objectOutput = new AppendableObjectOutputStream(fos);
        	// Store the content hash if necessary
        	if (fileHash==null || !fileHash.equals(contentHash)) {
        		fileHash=Integer.valueOf(contentHash);
        		objectOutput.writeObject(fileHash);
        	}
        	objectOutput.reset();
        	objectOutput.flush();
        	if (log.isDebugEnabled())
        		log.debug(String.format("Persistent queue file %s has been rewritten.", filename));
		} catch (IOException e) {
			log.warn(String.format("IOException opening queue file %s: %s", filename, e.toString()));
		}
        
        OperationsSinceDefragment = 0;
    }
    
    private void deleteQueueFile() {
        // Close the existing output stream
        if (objectOutput != null)
        	try {
        		objectOutput.close();
        	} catch (Exception e) {}
        objectOutput = null;
        
        if (!queueFile.delete() && usePersistance)
        	log.error(String.format("Error deleting queue file %s", queueFile.getAbsolutePath()));    	
    }
    
    /**
     * Marker is added to the queue file if an entry is removed.
     * 
     * @author chi
     *
     */
    private enum QueueMarker implements Serializable {
    	Remove, Clear
    }
    
    /**
     * An ObjectOutputStream writes the stream header every time it is constructed. So when data is appended
     * to an existing file thsi will result in a StreamCorruptedException because there are multiple headers.
     * The AppendableObjectOutputStream simply does not write the header.
     * 
     *
     */
	private class AppendableObjectOutputStream extends ObjectOutputStream {

		public AppendableObjectOutputStream(OutputStream out) throws IOException {
			super(out);
		}

		@Override
		protected void writeStreamHeader() throws IOException {
			// do not write a header
		}

	}

}
