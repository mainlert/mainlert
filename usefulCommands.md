clear && ./gradlew assembleDebug --no-daemon 2>&1

clear && ./logger.sh -c -l V -d 192.168.0.104

adb -s 06157df68c395438 logcat -c && clear && ./logger.sh -c -l V -d 06157df68c395438

clear && ./gradlew uninstallDebug installDebug
