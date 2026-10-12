package com.mohamedgamal.masareef;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Opens a user-reviewed draft; never reads the ledger or sends a request itself. */
final class EmailSupport {
    static final String ADDRESS="mgamal.ahmed@outlook.com";
    private EmailSupport() {}

    static String subject(boolean suggestion) {
        return "Follow Money · "+(suggestion?"اقتراح تحسين":"الإبلاغ عن مشكلة");
    }

    static String body(Context context,String description) {
        return description.trim()+"\n\n---\n"+GitHubSupport.details(context);
    }

    static Intent draft(Context context,boolean suggestion,String description) {
        String subject=subject(suggestion),body=body(context,description);
        Uri uri=Uri.parse("mailto:"+ADDRESS+"?subject="+Uri.encode(subject)+"&body="+Uri.encode(body));
        return new Intent(Intent.ACTION_SENDTO,uri).putExtra(Intent.EXTRA_SUBJECT,subject).putExtra(Intent.EXTRA_TEXT,body);
    }

    static String copyText(Context context,boolean suggestion,String description) {
        return ADDRESS+"\n"+subject(suggestion)+"\n\n"+body(context,description);
    }
}
