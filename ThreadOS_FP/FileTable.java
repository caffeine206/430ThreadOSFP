/*
Derek Willms, Brian Quigley, Robert Brandenburg
File table keeps track of all the files currently in ThreadOS
 */

import java.util.Vector;

public class FileTable {
    private Vector table; // the actual entity of this file table
    private Directory dir; // the root directory

    // constructor
    public FileTable(Directory dir) {
        table = new Vector(); // instantiate a file (structure) table
        this.dir = dir; // receive a reference to the directory from the file system
    }

    // allocate a new file (structure) table entry for this file name
    // allocate/retrieve and register the corresponding inode using dir
    // increment this inode's count
    // immediately write back this inode to the disk
    // return a reference to this file (structure) table entry
    public synchronized FileTableEntry falloc(String filename, String mode) {
        short iNumber; // file number
        Inode inode = null;
        boolean needNode = true;
        if (filename.equals("/")) { // root directory = 0
            iNumber = 0;
        } else {
            iNumber = dir.namei(filename);
        }
        while (true) {
            if (iNumber < 0)
                break;
            // create an inode with the number given
            inode = new Inode(iNumber);
            if (mode.equals("r")) { // read
                if (inode.flag == 0) {
                    inode.flag = 1;
                    // else if read, write, or delete try waiting
                    // to see if it's free
                } else if (inode.flag > 1) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                    // woke up so restart
                    continue;
                }
                needNode = false; // do not need a new inode
                break;
            }
            // not a read
            else {
                // if it's unused or write change to read now
                // since write is finished
                if (inode.flag == 0 || inode.flag == 3) {
                    inode.flag = 2;
                    needNode = false; // do not need a new inode
                    break;
                }
                if (inode.flag == 1 || inode.flag == 2) { // used or read already
                    inode.flag = 4;
                    inode.toDisk(iNumber);
                }
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }

        }
        if (!mode.equals("r") && needNode == true) { // not reading and need to allocate a new node
            iNumber = dir.ialloc(filename);
            inode = new Inode();
            inode.flag = 2;
        } else if (needNode == true) { // if we still need a node
            return null;
        }
        // update count, send to disk, and add new file table entry to the table
        inode.count++;
        inode.toDisk(iNumber);
        FileTableEntry fte = new FileTableEntry(inode, iNumber, mode);
        table.addElement(fte);
        return fte;
    }

    // receive a file table entry reference
    // save the corresponding inode to the disk
    // free this file table entry.
    // return true if this file table entry found in my table
    public synchronized boolean ffree(FileTableEntry filetableentry) {
        // attempt to remove entry from table
        if (table.removeElement(filetableentry)) {
            // update table counts to reflect removal
            filetableentry.inode.count--;
            filetableentry.inode.flag = 0;
            filetableentry.inode.toDisk(filetableentry.iNumber);
            filetableentry = null; // deallocate
            notify();
            return true;
        }
        // could not remove entry
        return false;
    }

    // return if table is empty
    // should be called before starting a format
    public synchronized boolean fempty() {
        return table.isEmpty();
    }
}