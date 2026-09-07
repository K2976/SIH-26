# Requirements Analysis Interview — FitForge Social Media Feature

## Questions

### 1. Workout Sharing & Privacy
When users share a workout from their diary (`diary.WorkoutSession`) to the public or followers feed, what details should be exposed by default?
A. High-level summary only: Exercise names, total sets, reps, and PR badges; all bodyweight, calorie, and biometrics data strictly excluded
B. Full session detail: Exercises, sets, weights, reps, and workout duration (calories, weight, and cycle data strictly excluded)
C. Configurable per post: User selects which stats to include (e.g. toggle weight, volume, notes), with physiological floors and biometrics excluded by default
X. Other (please specify)

[Answer]: C

### 2. Feed Discovery & Organization
What feed views should be supported on the Android application for v1?
A. Dual-tab feed: "Following" (chronological updates from followed athletes) and "Explore" (public community posts and notable achievements)
B. Single chronological Following feed only (strict time order of followed profiles)
C. Community feed only: single stream of all public posts with search/filter by handle
X. Other (please specify)

[Answer]: A

### 3. Profile Onboarding & Identity
When and how should an existing FitForge user create their public social `@handle`?
A. Prompted on first navigation to the Social tab, picking a unique @handle and display name before participating
B. Seamless read-only access: users can view public feeds without a profile, but must pick an @handle to like, comment, follow, or post
C. Auto-generated handle proposal derived from user account with one-click confirmation
X. Other (please specify)

[Answer]: B

### 4. Media Attachments for Posts
What media attachment capabilities should be supported for v1 posts?
A. Up to 4 images per post with local image compression on Android; video deferred to v2
B. Text-only and rich workout metric cards in v1; image/video attachments deferred to v2
C. Single photo or short video clip (up to 30 seconds) per post
X. Other (please specify)

[Answer]: B

### 5. Moderation & Safety Controls
What user safety and content moderation controls are required for the initial social launch?
A. User blocking, post/comment reporting, and post author authority to delete comments on their own posts
B. Minimal baseline: post author can delete comments on their posts; reporting handled via support contact
C. Comprehensive: blocking, reporting, automated profanity/abuse filtering, and ability to restrict comments to followers only
X. Other (please specify)

[Answer]: C

### 6. Offline & Sync Resilience (Android)
How should social interactions behave when an Android user has poor or no gym connectivity?
A. Cached feed reading with optimistic local queuing: likes and drafted posts are saved to Room and synced via WorkManager when back online
B. Cached feed reading: browsing previously loaded posts works offline; posting, commenting, and liking require an active network connection
C. Network-required: display an offline status indicator on the Social screen; require network connectivity for all social actions
X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

Summary of confirmed requirements decisions:
- Workout Sharing & Privacy: Configurable per-post metrics with strict exclusion of bodyweight, calorie, and biometrics data.
- Feed Discovery & Organization: Dual-tab feed on Android ("Following" for followed updates and "Explore" for community discovery).
- Profile Onboarding & Identity: Seamless read-only browsing; users pick a unique @handle when performing social actions (post, like, comment, follow).
- Media Attachments: Text-only and structured workout metric cards in v1; image and video attachments deferred to v2.
- Moderation & Safety: Comprehensive safety controls including user blocking, reporting, automated profanity/abuse filtering, and optional follower-only commenting.
- Offline & Sync Resilience: Offline feed browsing from local cache; social interactions (post, like, comment) require network connectivity.

Does this all look correct before I generate the requirements artifact?
- Looks correct
- Request changes

[Answer]: Looks correct
