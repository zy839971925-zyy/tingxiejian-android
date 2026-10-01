package com.example.tingxiejian;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
/** Access only manifest-supplied relative files through the user's selected SAF tree. */
final class SafModelImporter {
    static InputStream open(Context context,Uri tree,String relative)throws IOException{
        String parent=DocumentsContract.getTreeDocumentId(tree);
        for(String name:relative.split("/")){
            if(name.isEmpty()||name.equals(".")||name.equals(".."))throw new IOException("模型路径无效");
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent);String found=null;
            try(Cursor c=context.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
                if(c!=null)while(c.moveToNext())if(name.equals(c.getString(1))){found=c.getString(0);break;}
            }
            if(found==null)throw new FileNotFoundException("模型包缺少 "+relative);parent=found;
        }
        InputStream in=context.getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree,parent));
        if(in==null)throw new IOException("无法读取模型文件");return in;
    }
}
