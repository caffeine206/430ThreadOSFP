/*
Derek Willms, Brian Quigley, Robert Brandenburg
Upon a boot, the file system instantiates the following Directory class as the root directory through its constructor,
reads the file from the disk that can be found through the inode 0 at 32 bytes of the disk block 1, and initializes
the Directory instance with the file contents. On the other hand, prior to a shutdown, the file system must write back
the Directory information onto the disk. The methods bytes2directory( ) and directory2bytes will initialize the
Directory instance with a byte array read from the disk and converts the Directory instance into a byte array that
will be thereafter written back to the disk.
 */

public class Directory {
    private static int maxChars = 30; // max characters of each file name

    // Directory entries
    private int fsize[];        // each element stores a different file size.
    private char names[][];    // each element stores a different file name.

    public Directory( int maxInumber ) { // directory constructor
        fsize = new int[maxInumber];     // maxInumber = max files
        for ( int i = 0; i < maxInumber; i++ )
            fsize[i] = 0;                 // all file size initialized to 0
        names = new char[maxInumber][maxChars];
        String root = "/";                // entry(inode) 0 is "/"
        fsize[0] = root.length( );        // fsize[0] is the size of "/".
        root.getChars( 0, fsize[0], names[0], 0 ); // names[0] includes "/"
    }

    // assumes data[] received directory information from disk
    // initializes the Directory instance with this data[]
    public void bytes2directory(byte data[]) {
        int offset = 0;
        // getting the filesizes from the data
        for (int i = 0; i < fsize.length; offset += 4) {
            fsize[i] = SysLib.bytes2int(data, offset);
            i++;
        }
        // getting the filenames
        for (int i = 0; i < names.length; offset += maxChars * 2) {
            String name = new String(data, offset, maxChars * 2);
            name.getChars(0, fsize[i], names[i], 0);
            i++;
        }
    }

    // converts and return Directory information into a plain byte array
    // this byte array will be written back to disk
    // note: only meaningfull directory information should be converted
    // into bytes.
    public byte[] directory2bytes() {
        // create the new
        byte[] newData = new byte[(4 * fsize.length) + (fsize.length * maxChars * 2)];
        int offset = 0;
        for (int i = 0; i < fsize.length; offset += 4) {
            SysLib.int2bytes(fsize[i], newData, offset);
            i++;
        }

        for (int i = 0; i < names.length; offset += maxChars * 2) {
            // get the file name
            String name = new String(names[i], 0, fsize[i]);
            byte[] str_bytes = name.getBytes(); // converting the filename string to bytes
            // write to the directory array
            System.arraycopy(str_bytes, 0, newData, offset, str_bytes.length);
            i++;
        }
        return newData;
    }

    // allocate the given file
    public short ialloc(String filename) {
        for (int i = 1; i < fsize.length; i++) {
            if (fsize[i] == 0) {
                fsize[i] = Math.min(filename.length(), maxChars);
                filename.getChars(0, fsize[i], names[i], 0);
                return (short)i;
            }
        }
        return -1;
    }

    // free up the given block
    public boolean ifree(short iNumber) {
        if (fsize[iNumber] > 0) {
            fsize[iNumber] = 0;
            return true;
        } else {
            return false;
        }
    }

    // names the given file
    public short namei(String filename) {
        // loop over fsize array
        for (int i = 0; i < fsize.length; i++) {
            String name = new String(names[i], 0, fsize[i]);
            // make sure the size matches and strings match
            if (fsize[i] == filename.length() && filename.equals(name)) {
                // return the iNumber
                return (short)i;
            }
        }
        return -1;
    }
}
