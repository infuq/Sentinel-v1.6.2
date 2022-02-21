package com.infuq;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;

public class Example {

    private static Context context;

    public static void main(String[] args) throws Exception {

        Entry entry = null;
        try {

            // #1
            context = ContextUtil.enter("contextName");


            entry = SphU.entry("resourceName:delete", EntryType.IN);
            entry = SphU.entry("resourceName:update1", EntryType.IN);


            entry = SphU.entry("resourceName:delete", EntryType.IN);
            entry = SphU.entry("resourceName:update2", EntryType.IN);
            entry = SphU.entry("resourceName:update3", EntryType.IN);

            System.out.println("success...");

        } catch (BlockException e) {
            e.printStackTrace();

        } finally {
            if (entry != null) {
                entry.exit();
            }
        }

/*
        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Entry entry = null;
                try {

                    // #2
                    Context _context = ContextUtil.enter("contextName");


                    entry = SphU.entry("resourceName:delete", EntryType.IN);
                    entry = SphU.entry("resourceName:update1", EntryType.IN);

                    entry = SphU.entry("resourceName:delete", EntryType.IN);
                    entry = SphU.entry("resourceName:update2", EntryType.IN);
                    entry = SphU.entry("resourceName:update3", EntryType.IN);
// com.alibaba.csp.sentinel.Constants.ROOT
                    System.out.println("success...");
                } catch (BlockException e) {
                    e.printStackTrace();
                } finally {
                    if (entry != null) {
                        entry.exit();
                    }
                }
            }
        }).start();
*/




    }


}
