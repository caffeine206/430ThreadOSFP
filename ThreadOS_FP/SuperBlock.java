/*
Derek Willms, Brian Quigley, Robert Brandenburg
The disk block 0 is called a superblock and used to describe (1) the number of disk blocks,
(2) the number of inodes, and (3) the block number of the head block of the free list. This is the OS-managed block.
No other information must be recorded in and no user threads must be able to get access to the superblock.
 */
public class SuperBlock {
    private final int defaultInodeBlocks = 64;
    public int totalBlocks; // the number of disk blocks
    public int totalInodes; // the number of inodes
    public int freeList; // the block number of the free list's head

    // SuperBlock constructor
    public SuperBlock(int diskSize) {
        // read the superblock from disk
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.rawread(0, superBlock);
        totalBlocks = SysLib.bytes2int(superBlock, 0);
        totalInodes = SysLib.bytes2int(superBlock, 4);
        freeList = SysLib.bytes2int(superBlock, 8);

        if ((totalBlocks == diskSize) && (totalInodes > 0) && (freeList >= 2)) { // disk contents are valid
            return;
        } else { // need to format disk
            totalBlocks = diskSize;
            format(defaultInodeBlocks);
        }
    }

    // Clear the given number of Inode blocks
    public void format(int inodeBlocks) {
        totalInodes = inodeBlocks;
        for (short i = 0; i < totalInodes; i++) {
            Inode inode = new Inode();
            inode.flag = 0;
            inode.toDisk(i);
        }
        freeList = 2 + totalInodes * 32 / Disk.blockSize;
        for (int i = freeList; i < totalBlocks; i++) {
            byte[] superBlock = new byte[Disk.blockSize];
            for (int j = 0; j < Disk.blockSize; j++) {
                superBlock[j] = 0;
            }
            SysLib.int2bytes(i + 1, superBlock, 0);
            SysLib.rawwrite(i, superBlock);
        }
        sync();
    }

    // Write back totalBlocks, totalInodes, and freeList to disk
    public void sync() {
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.int2bytes(totalBlocks, superBlock, 0);
        SysLib.int2bytes(totalInodes, superBlock, 4);
        SysLib.int2bytes(freeList, superBlock, 8);
        SysLib.rawwrite(0, superBlock);
    }

    // Dequeue the top block from the free list
    public int getFreeBlock() {
        int index = freeList;
        if (index != -1) {
            byte[] superBlock = new byte[Disk.blockSize];

            SysLib.rawread(index, superBlock);
            freeList = SysLib.bytes2int(superBlock, 0);

            SysLib.int2bytes(0, superBlock, 0);
            SysLib.rawwrite(index, superBlock);
        }
        return index;
    }

    // Enqueue a given block to the end of the free list
    public boolean returnBlock(int blockNumber) {
        if (blockNumber >= 0) {
            byte[] superBlock = new byte[Disk.blockSize];
            for (int i = 0; i < Disk.blockSize; i++) {
                superBlock[i] = 0;
            }
            SysLib.int2bytes(freeList, superBlock, 0);
            SysLib.rawwrite(blockNumber, superBlock);
            freeList = blockNumber;
            return true;
        }
        return false;
    }
}
