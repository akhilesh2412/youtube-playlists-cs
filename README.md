# Free Courses — CloudStream plugin

Free public YouTube playlists ke liye CloudStream plugin.
Sirf official public YouTube playlists use karta hai — koi paid / unofficial mirror nahi.

## Playlists (4)

1. **CUET MCA Preparation 2026** — `PL-CL6RQ4EUMaSvMpCq3ySkt63UfLGcmVW` (57 videos)
   https://youtube.com/playlist?list=PL-CL6RQ4EUMaSvMpCq3ySkt63UfLGcmVW
2. **CUET PG MCA 2027 - Complete Computer Science | Adda247** — `PLzlQDutMQpIgfi6TFtUMMkWcgVHPboczh` (40 videos)
   https://youtube.com/playlist?list=PLzlQDutMQpIgfi6TFtUMMkWcgVHPboczh
3. **C Programming Course** — `PLxgZQoSe9cg1drBnejUaDD9GEJBGQ5hMt` (12 videos)
   https://youtube.com/playlist?list=PLxgZQoSe9cg1drBnejUaDD9GEJBGQ5hMt
4. **C Programming** — `PLxCzCOWd7aiGmiGl_DOuRMJYG8tOVuapB` (48 videos)
   https://youtube.com/playlist?list=PLxCzCOWd7aiGmiGl_DOuRMJYG8tOVuapB

Nayi playlist add karni ho to `FreeCoursesProvider.kt` me `playlists` list me ek line badha do.

## Kaise kaam karta hai

- Playlist data Invidious public API (`/api/v1/playlists/{id}`) se aata hai.
- Koi Invidious instance down ho to dusra try hota hai, phir YouTube playlist page fallback hai.
- Video play ke liye YouTube watch URL CloudStream ke built-in YouTube extractor ko diya jata hai.

## Setup

1. GitHub pe **public** repo banao naam `youtube-playlists-cs`, ye files daalo.
   `build.gradle.kts` me `iconUrl` aur `authors` me apna GitHub username daal lo (abhi `sky` likha hai).
2. Actions tab → workflow chalne do. Green hone ke baad `builds` branch ban jayegi jismein `repo.json` + `.cs3` honge.
3. CloudStream → Settings → Extensions → Add repository:
   ```
   https://raw.githubusercontent.com/<username>/youtube-playlists-cs/builds/repo.json
   ```
4. Extension install karo → "Free Courses" provider khulega → Playlist → episode → play.

## Note

Ye 4 playlists Apna College ki nahi hain — ye CUET MCA / Adda247 / C Programming ki public free YouTube playlists hain, isliye naam bhi "Free Courses" rakha hai, "Apna College" nahi.
