package com.mohamedgamal.masareef;

import java.util.ArrayList;
import java.util.List;

/** Related details return to their parent before returning to the original list. */
final class TransactionJourney {
    private final List<Long> parents=new ArrayList<>();
    void clear(){parents.clear();}
    void visit(long current,long next){if(current!=next)parents.add(current);}
    Long back(){return parents.isEmpty()?null:parents.remove(parents.size()-1);}
    long[] snapshot(){long[] ids=new long[parents.size()];for(int i=0;i<ids.length;i++)ids[i]=parents.get(i);return ids;}
    void restore(long[] ids){clear();if(ids!=null)for(long id:ids)parents.add(id);}
}
