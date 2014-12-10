/**
 * Derek Willms, Brian Quigley, Robert Brandenburg
 * Each inode describes one file. Our inode is a simplified version of the Unix inode (as explained in our textbook.)
 * It includes 12 pointers of the index block. The first 11 of these pointers point to direct blocks.
 * The last pointer points to an indirect block. In addition, each inode includes:
 * (1) the length of the corresponding file,
 * (2) the number of file (structure) table entries that point to this inode, and
 * (3) the flag to indicate if it is unused (= 0), used(= 1), or in some other status
 * 16 inodes can be stored in one block.
 */
public class Inode {
    public static final int iNodeSize = 32;
    public static final int directSize = 11;
    public int length;
    public short count;
    public short flag;
    public short[] direct = new short[directSize];
    public short indirect;

    // constructor with given number for inode
    public Inode(short iNumber) {
        int blockNumber = 1 + iNumber / 16;
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(blockNumber, data);
        int offset = (iNumber % 16) * iNodeSize;

        length = SysLib.bytes2int(data, offset);
        offset += 4;
        count = SysLib.bytes2short(data, offset);
        offset += 2;
        flag = SysLib.bytes2short(data, offset);
        offset += 2;

        for (int i = 0; i < directSize; i++) {
            direct[i] = SysLib.bytes2short(data, offset);
            offset += 2;
        }
        indirect = SysLib.bytes2short(data, offset);
    }

    // default constructor
    public Inode() {
        length = 0;
        count = 0;
        flag = 0;
        for (int i = 0; i < directSize; i++) {
            direct[i] = -1; // intialize each index to -1 (unused)
        }
        indirect = -1;
    }

    //save to disk as the ith node
    public void toDisk(short iNumber) {
        // array for the data to get values
        byte[] data = new byte[iNodeSize];
        int offset = 0;

        // get the length
        SysLib.int2bytes(length, data, offset);
        offset += 4;
        // get the count
        SysLib.short2bytes(count, data, offset);
        offset += 2;
        // get the flag
        SysLib.short2bytes(flag, data, offset);
        offset += 2;

        // get the pointers
        for (int i = 0; i < directSize; i++) {
            SysLib.short2bytes(direct[i], data, offset);
            offset += 2;
        }
        // get final indirect
        SysLib.short2bytes(indirect, data, offset);

        // read new data
        int block = 1 + iNumber / 16;
        byte[] newData = new byte[Disk.blockSize];
        SysLib.rawread(block, newData);
        offset = iNumber % 16 * iNodeSize;

        // copy the new data
        System.arraycopy(data, 0, newData, offset, iNodeSize);
        // write back new data
        SysLib.rawwrite(block, newData);
    }

    // sets index block at given block number
    // returns false if any any of the directs are not set
    public boolean setIndexBlock(short indexBlockNumber) {
        // loop over direct array, and if any are -1, then its not set and return false
        for (int i = 0; i < directSize; i++) {
            if (direct[i] == -1) {
                return false;
            }
        }

        if (indirect != -1) {
            return false;
        } else {
            indirect = indexBlockNumber;
            byte[] data = new byte[Disk.blockSize];

            for (int i = 0; i < Disk.blockSize / 2; i++) {
                SysLib.short2bytes((short) -1, data, i * 2);
            }

            SysLib.rawwrite(indexBlockNumber, data);
            return true;
        }
    }

    // takes a byte index and returns the data from that block
    public int findBlockNumber(int byteNumber) {
        // each block contains 512 bytes, so we find the block number by dividing the byteNumber by 512.
        int blockNumber = byteNumber / Disk.blockSize;
        if (blockNumber < directSize) {
            return direct[blockNumber];
        }
        if (indirect < 0) {
            return -1;
        }
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(indirect, data);
        int offset = (blockNumber - directSize) * 2;
        return (int) SysLib.bytes2short(data, offset);
    }

    // attempts to write the given block and returns a code to represent the result of the attempt
    // 0 = good to write, 1 = in use, 2 = indirect is empty
    public int submitBlock(int pointer, short freeBlock) {
        int location = pointer / Disk.blockSize;
        if (location < directSize) { // if found in direct
            if (direct[location] >= 0) // in use if not clean (0)
                return 1;
            if ((location > 0) && (direct[(location - 1)] == -1)) // good to write
                return 0;
            direct[location] = freeBlock; // update location
            return 0;
        }
        if (indirect < 0) { // indirect empty
            return 2;
        }
        // read indirect into data and write after adjusting for offset
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(indirect, data);
        int offset = (location - directSize) * 2;
        if (SysLib.bytes2short(data, offset) > 0) { // in use
            return 1;
        }
        SysLib.short2bytes(freeBlock, data, offset);
        SysLib.rawwrite(indirect, data);
        return 0;
    }

    // release the indirect and return the data
    byte[] releaseIndirect() {
        // if indirect is valid,read the raw data, set to free and then return data
        if (indirect >= 0) {
            byte[] data = new byte[Disk.blockSize];
            SysLib.rawread(indirect, data);
            indirect = -1;
            return data;
        }
        // else return null
        return null;
    }
}