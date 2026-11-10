# Что установить в Android Studio (один раз)

Открой Android Studio -> More Actions (или значок настроек) -> SDK Manager.

## Вкладка SDK Platforms
- Android 15 (API 35) — нужен для сборки (если нет, поставь галочку и Apply).
- Для эмулятора часов отдельно ничего ставить не надо, образ скачивается в Device Manager.

## Вкладка SDK Tools (галочки)
- Android SDK Build-Tools, Android SDK Platform-Tools, Android Emulator,
- Google Play services (по желанию), Intel x86 Emulator Accelerator (HAXM) — если пишет про ускорение.

## Эмуляторы (нужны с блока 6; до этого хватит одного эмулятора часов)
Tools -> Device Manager -> «+» (Create Device):
1. Часы: категория Wear OS -> «Wear OS Large Round» -> образ Wear OS 5 (API 34), скачать -> Finish.
2. Телефон (нужен для связки): категория Phone -> Pixel 6 -> образ с пометкой «Google Play»
   (значок магазина) API 34 или 35 -> Finish. Образы без Google Play не соединяются с часами.
3. Сопряжение: Device Manager -> Pair Wearable (значок рядом с часами) и следуй подсказкам.

## Требования компьютера
- Виртуализация включена в BIOS (Intel VT-x / AMD-V); 8 ГБ ОЗУ и больше.
- Эмулятор часов лёгкий. Если два эмулятора тормозят, запускай только часы до блока 6.

## Запуск модуля часов
Сверху выбери конфигурацию «wear» (список рядом с зелёной ▶), выбери эмулятор часов и нажми ▶.
Телефонное приложение запускается конфигурацией «app».
