/*
Derek Willms, Brian Quigley, Robert Brandenburg
a Unix-like file system on our ThreadOS. User thread programs will be now relieved from painful direct access to
disk blocks and given a vision of stream-oriented files.
 */
public class FileSystem {
    private SuperBlock superblock; // superblock variable
    private Directory directory; // directory variable
    private FileTable filetable; // filetable variable

    // constructor
    public FileSystem(int diskBlocks) {
        superblock = new SuperBlock(diskBlocks);
        directory = new Directory(superblock.totalInodes);
        filetable = new FileTable(directory);

        // read the "/" file from disk
        FileTableEntry fte = open("/", "r");
        int dirSize = fsize(fte);
        if (dirSize > 0) {
            byte[] dirData = new byte[dirSize];
            read(fte, dirData);
            directory.bytes2directory(dirData);
        }
        close(fte);
    }

    public void sync() {
        // open root directory
        FileTableEntry fte = open("/", "w");
        byte[] data = directory.directory2bytes();
        // write and then close
        write(fte, data);
        close(fte);
        superblock.sync();
    }


    // formats the disk, (i.e., Disk.java's data contents).
    // The parameter files specifies the maximum number of files to be created,
    // (i.e., the number of inodes to be allocated) in your file system. The return value is 0 on success, otherwise -1.
    public boolean format(int files) {
        while (!filetable.fempty()) { // wait for the file table to empty
        }
        // format and then update directory / file table
        superblock.format(files);
        directory = new Directory(superblock.totalInodes);
        filetable = new FileTable(directory);
        return true;
    }

    // reads up to buffer.length bytes from the file indicated by fd, starting at the position currently pointed
    // to by the seek pointer. If bytes remaining between the current seek pointer and the end of file are less than
    // buffer.length, SysLib.read reads as many bytes as possible, putting them into the beginning of buffer.
    // It increments the seek pointer by the number of bytes to have been read. The return value is the number of
    // bytes that have been read, or a negative value upon an error.
    public int read(FileTableEntry fte, byte[] buffer) {
        // mode must be read
        if (fte == null || (fte.mode == "a") || (fte.mode == "w")) {
            return -1;
        }
        int bufferIndex = 0;
        int remainingBufferLength = buffer.length;
        int fileSize = fsize(fte);
        synchronized (fte) {
            // while loop to continue reading until we are finished reading from the starting position of the file's seekPtr to the end of the file,
            // or until the buffer is full.
            while (remainingBufferLength > 0 && fte.seekPtr < fileSize) {
                int blockNumber = fte.inode.findBlockNumber(fte.seekPtr);
                if (blockNumber == -1) {
                    return bufferIndex;
                }
                // read data from the current block into a byte buffer
                byte[] blockData = new byte[Disk.blockSize];
                SysLib.rawread(blockNumber, blockData);
                // find the offset to start reading from
                int offset = fte.seekPtr % Disk.blockSize;
                // find number of bytes to read in the current block
                int blockReadLength = Disk.blockSize - offset;
                // find number of bytes to read based on the size of the file and its current seekPtr position
                int fileReadLength = fileSize - fte.seekPtr;
                // if the buffer is too small to read the segment of the block from offset to the end or the rest of the file, then the buffer's size is the number of bytes read.
                // otherwise, the number of bytes read is based on whichever is smaller between blockReadLength and fileReadLength.
                int readLength = Math.min(Math.min(blockReadLength, remainingBufferLength), fileReadLength);
                // call arraycopy to transfer the appropriate data from blockData to the buffer.
                System.arraycopy(blockData, offset, buffer, bufferIndex, readLength);

                // adjust the values of the file's seekPtr, the remaining buffer length, and the current buffer index based on the read that has just occurred.
                remainingBufferLength -= readLength;
                fte.seekPtr += readLength;
                bufferIndex += readLength;
            }
            // return the location
            return bufferIndex;
        }
    }

    // this function writes the data from the buffer to the file
    //
    public int write(FileTableEntry fte, byte[] buffer) {
        int location = 0;

        // not given a valid file table entry or we are suppose to
        // read instead of write
        if (fte == null || fte.mode == "r") {
            return -1;
        }
        // go into critical section
        synchronized (fte) {
            int buffLength = buffer.length;
            while (buffLength > 0) { // loop over buffer
                int currentBlock = fte.inode.findBlockNumber(fte.seekPtr); // try to find the given block
                if (currentBlock == -1) { // need find a free block
                    short freeBlock = (short) superblock.getFreeBlock();
                    // attempt to submit block, then act based on return code
                    int status = fte.inode.submitBlock(fte.seekPtr, freeBlock);
                    switch ( status )
                    // 1 = good to write, -1 = in use, 0 = indirect is empty
                    {
                        case Inode.INDIRECT_IN_USE:
                            SysLib.cerr("Filesystem error on write\n");
                            return -1;
                        case Inode.INDIRECT_EMPTY: // indirect is empty, search for new location
                            freeBlock = (short) superblock.getFreeBlock();
                            status = fte.inode.submitBlock(fte.seekPtr, freeBlock); // attempt to submit location
                            if (!fte.inode.setIndexBlock((short) status)) { // attempt to set index to new location
                                SysLib.cerr("Filesystem error on write\n");
                                return -1;
                            }
                            // attempt submit block again
                            if ( fte.inode.submitBlock(fte.seekPtr, freeBlock) != Inode.INDIRECT_AVAILABLE ) {
                                SysLib.cerr("Filesystem error on write\n");
                                return -1;
                            }
                            break;
                    }
                    // update location
                    currentBlock = freeBlock;
                }

                byte[] data = new byte[Disk.blockSize];
                // Attempt to read
                if (SysLib.rawread(currentBlock, data) == -1) {
                    System.exit(2);
                }

                // find write location based on given pointer and disk size
                int diskLocation = fte.seekPtr % Disk.blockSize;
                int adjustedLocation = Disk.blockSize - diskLocation;
                int length = Math.min(adjustedLocation, buffLength);

                // copy into data and write
                System.arraycopy(buffer, location, data, diskLocation, length);
                SysLib.rawwrite(currentBlock, data);

                // update variables according to given location
                fte.seekPtr += length;
                location += length;
                buffLength -= length;

                // update inode to reflect adjusted length
                if (fte.seekPtr > fte.inode.length) {
                    fte.inode.length = fte.seekPtr;
                }
            }
            // send the inode to disk and return adjusted location
            fte.inode.toDisk(fte.iNumber);
            return location;
        }
    }

    // Updates the seek pointer corresponding to fd
    int seek(FileTableEntry fte, int offset, int whence) {
        //check for valid filetableEntry
        if (fte == null) {
            SysLib.cerr("invalid file table");
            return -1;
        }

        // **Critical section
        // if whence is set to 0 set seek pointer to offset
        // else if whence is set to 1 set to current value plus offset
        // else if whence is set to 2 set the pointer to the size of the file plus offset
        synchronized (fte) {
            switch (whence) {
                case 0:
                    if ((offset >= 0) && (offset <= fsize(fte))) {
                        fte.seekPtr = offset;
                    } else {
                        return -1;
                    }
                    break;
                case 1:
                    if ((fte.seekPtr + offset >= 0) && (fte.seekPtr + offset <= fsize(fte))) {
                        fte.seekPtr += offset;
                    } else {
                        return -1;
                    }
                    break;
                case 2:
                    if ((fsize(fte) + offset >= 0) && (fsize(fte) + offset <= fsize(fte))) {
                        fte.seekPtr = (fsize(fte) + offset);
                    } else {
                        return -1;
                    }
                    break;
            }
            return fte.seekPtr;
        }
    }

    // invalid offset error
    public void invalidOffset() {
        SysLib.cerr("invalid offset");
    }

    // opens the file specified by the fileName string in the given mode
    // (where "r" = ready only, "w" = write only, "w+" = read/write, "a" = append),
    // and allocates a new file descriptor, fd to this file.
    public FileTableEntry open(String fileName, String mode) {
        FileTableEntry fte = filetable.falloc(fileName, mode);
        if (fte != null && mode == "w" && !deallocAllBlocks(fte)) { // no place to write
            return null;
        }
        return fte;
    }

    // closes the file corresponding to fd, commits all file transactions on this file, and unregisters fd from the
    // user file descriptor table of the calling thread's TCB. The return value is 0 in success, otherwise -1.
    public boolean close(FileTableEntry fte) {
        synchronized (fte) {
            fte.count--;
            if (fte.count > 0) {
                return true;
            }
        }
        return filetable.ffree(fte);
    }

    // destroys the file specified by fileName. If the file is currently open, it is not destroyed until the last open on it is closed, but new attempts to open it will fail.
    public boolean delete(String fileName) {
        FileTableEntry fte = open(fileName, "w");
        // if there is no file then return false
        if (fte == null) {
            return false;
        }
        return close(fte) && directory.ifree(fte.iNumber);
    }

    // clears inode and frees blocks
    private boolean deallocAllBlocks(FileTableEntry fileTableEntry) {
        // check valid inode and filetableentry
        if (fileTableEntry.inode.count != 1 || fileTableEntry == null) {
            return false;
        }
        // release indirect Inode
        byte[] releasedBlocks = fileTableEntry.inode.releaseIndirect();

        // if not free release indirect blocks
        if (releasedBlocks != null) {
            int num = SysLib.bytes2short(releasedBlocks, 0);
            while (num != -1) {
                superblock.returnBlock(num);
            }
        }

        // release direct blocks
        // if direct block exists, then release it and mark invalid
        for (int i = 0; i < Inode.directSize; i++)
            if (fileTableEntry.inode.direct[i] != -1) {
                superblock.returnBlock(fileTableEntry.inode.direct[i]);
                fileTableEntry.inode.direct[i] = -1;
            }

        // finally writeback Inode
        fileTableEntry.inode.toDisk(fileTableEntry.iNumber);
        return true;
    }

    // this function returns the size in bytes for the given file
    public int fsize(FileTableEntry fte) {
        synchronized (fte) {
            return fte.inode.length;
        }
    }
}