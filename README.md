## Music

Офлайн-плеер локальной музыки на Kotlin + Jetpack Compose + Media3.

### Обновления через GitHub

Приложение умеет проверять новые версии через **GitHub Releases** и предлагать установить APK.

#### 1. Создай репозиторий на GitHub

Репозиторий должен быть **публичным**: приложение обращается к GitHub API без токена, а для приватных репозиториев API вернёт 404.

Загрузи проект (GitHub Desktop или git):

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/USER/REPO.git
git push -u origin main
```

#### 2. Добавь секреты репозитория

`Settings → Secrets and variables → Actions → New repository secret`:

| Секрет | Значение |
|---|---|
| `MUSIC_KEYSTORE_BASE64` | keystore в base64 (см. ниже) |
| `MUSIC_KEYSTORE_PASSWORD` | пароль keystore |
| `MUSIC_KEY_ALIAS` | псевдоним ключа |
| `MUSIC_KEY_PASSWORD` | пароль ключа |

Чтобы обновление устанавливалось **поверх уже установленного приложения**, подпись должна совпадать. Сейчас приложение подписано debug-ключом Android Studio — используй его же:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.android\debug.keystore"))
```

- alias: `androiddebugkey`
- пароли: `android`
- файл: `%USERPROFILE%\.android\debug.keystore`

Для «взрослого» релиза сгенерируй отдельный keystore (`keytool -genkeypair ...`), но тогда первое обновление потребует удалить старую версию приложения (подписи не совпадут).

#### 3. Выпусти релиз

```bash
git tag v1.0.1
git push origin v1.0.1
```

GitHub Actions соберёт подписанный APK и создаст релиз `v1.0.1` с файлом `Music-1.0.1.apk`.
`versionName` берётся из тега (`1.0.1`), `versionCode` — из номера запуска workflow.

Нумерация — три числа: `1.0.1`, `1.0.2`, `1.1.0`. Каждый следующий релиз должен быть **численно больше** установленного, иначе приложение не предложит обновление. Тестовые версии в релизы не публикуем — только когда есть что показать.

#### 4. Включи проверку в приложении

`Настройки → Обновления`:

- включи «Проверять обновления»;
- в поле «Репозиторий GitHub» впиши `USER/REPO`;
- нажми «Проверить сейчас» (при запуске проверка идёт сама).

Когда появится новая версия, приложение предложит установить её. Android попросит разрешить установку из этого приложения («Установка неизвестных приложений») — это системное требование, обойти его нельзя.

### Сборка локально

```powershell
.\gradlew.bat assembleDebug
```

Release-сборка подписывается только если заданы переменные окружения `MUSIC_KEYSTORE`, `MUSIC_KEYSTORE_PASSWORD`, `MUSIC_KEY_ALIAS`, `MUSIC_KEY_PASSWORD` (так делает CI).
