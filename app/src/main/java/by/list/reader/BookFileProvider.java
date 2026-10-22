package by.list.reader;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Temporary read-only grants for other readers; no public filesystem exposure. */
public class BookFileProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException{
        String id=uri.getPathSegments().isEmpty()?"":uri.getPathSegments().get(0);
        if(!id.matches("[a-zA-Z0-9-]+"))throw new FileNotFoundException("Invalid book ID");
        File file=new File(new File(getContext().getFilesDir(),"originals"),id);
        if(!file.isFile())throw new FileNotFoundException("Book file missing");return file;
    }
    public static String mime(String name){String n=name.toLowerCase(java.util.Locale.ROOT);return n.endsWith(".epub")?"application/epub+zip":n.endsWith(".zip")?"application/zip":n.endsWith(".fb2")?"application/fb2+xml":"text/plain";}
    public String getType(Uri uri){return mime(uri.getLastPathSegment());}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor cursor=new MatrixCursor(cols);Object[] row=new Object[cols.length];
        try{File f=file(uri);for(int i=0;i<cols.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?uri.getLastPathSegment():OpenableColumns.SIZE.equals(cols[i])?f.length():null;cursor.addRow(row);}catch(Exception ignored){}return cursor;
    }
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
