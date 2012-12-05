#!/usr/bin/env groovy
import java.nio.channels.*

class Lock { 
    public static boolean getLock() {
        //起動チェック
        final FileOutputStream fos = new FileOutputStream(new File("lock"))
        final FileChannel fc = fos.channel
        final FileLock lock = fc.tryLock()

        if (lock == null) {
            return false
        }

        //ロック開放処理を登録
        Runtime.runtime.addShutdownHook {
            if (lock != null && lock.valid) {
                lock.release()
            }
            fc.close()
            fos.close()
        } as Thread

        return true
    }
}
