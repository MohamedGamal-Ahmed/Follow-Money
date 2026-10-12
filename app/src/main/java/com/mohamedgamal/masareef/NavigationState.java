package com.mohamedgamal.masareef;

/** Maps existing screen IDs to their navigation destination, including child screens. */
final class NavigationState {
    static int destination(int screen,int transactionReturn) {
        if(screen==10) return destination(transactionReturn==10?6:transactionReturn,6);
        if(screen==6) return 6;
        if(screen==2 || screen==11 || screen==12) return 2;
        if(screen==3 || screen==4 || screen==8 || screen==9 || screen==13 || screen==14) return 4;
        return 0; // Home, accounts, Settings and merchant rules.
    }
    private NavigationState() {}
}
