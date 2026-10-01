<p align="center"><a href="UI-MOTION.md">简体中文</a> · <strong>English</strong></p>

# UI and motion decisions (source review, not device acceptance)

> This retains the v1.0.3 design record. Current ASR, automatic correction, live dictation, independent haptics and notification changes are documented in the [upgrade record](upgrade/IMPLEMENTATION.md) and [Live Update record](upgrade/LIVE-UPDATES.md) (Chinese).

## Intent and boundaries

Motion should communicate **origin, direction, state and feedback**, not perform a spectacle on
every tap. Frequent file selection, progress updates, keyboard input and message scrolling stay
direct. The first-run guide and task completion can have one-off emphasis. Android's file picker,
notification permission dialog, share sheet and predictive back belong to system windows;
the app cannot promise a frame-by-frame continuous transition across them.

| Route | Enter / exit | Reduced motion or interruption |
| :--- | :--- | :--- |
| First-run guide → home | Button-centered circle expands into a full-screen color; home overlay fades and content enters in sequence | Switch immediately; overlay times out and clears; page state is retained |
| Guide pages | 28dp directional travel, short exit then eased entry; final icon bounces once | Change pages immediately; reset alpha/position on stop |
| Settings → guide | Light lateral window entry and reverse exit | Immediate switch if animations are disabled |
| Home → Settings / transcript → chat | Native shared-element container expands; content enters shortly after; return contracts to source control | New routes switch directly; a route already started completes its return if preferences change mid-flight |
| Four home panels | Short exit, slightly delayed entry; model-prep/completion icon reacts after the card settles | Cancel old animation and normalize all panels; no effect when switching with motion reduced |
| Third-party terms | Short bidirectional fade; checkbox unlocks the primary action | Complete the action immediately when animation is disabled |
| Chat messages | Animate only a new message once; do not replay history | Show immediately with animations disabled |

## v1.0.3 source review

| Concern | Change | Limit |
| :--- | :--- | :--- |
| Large type and home hierarchy | Replaced fixed 66dp header height with minimum 66dp and natural text growth; fixed action area gets its own surface and fine divider | Still needs small-screen / 200% font-size device checks |
| Supporting copy | Caption/Meta moved from 12sp to 13sp; model button, cloud inputs and chat input targets enlarged to at least 48dp | Touch width and high-contrast appearance still need device measurement |
| Offline default vs optional ASR | Home syncs settings on return and warns that configured cloud ASR sends audio to the chosen service | Platform-owned pickers are not given fake transitions |
| Animation frequency | One-off guide/completion feedback retained; no looping word animation for old chat messages, progress numbers or typed text | Low-end-device frame time needs recording and measurement |

## Implementation choices

- The app is **Java + Android Views, built with aapt/javac**, not a web app. GSAP SplitText is a
  Web/DOM tool. Adding WebView and JavaScript for a few title lines would raise startup,
  accessibility and maintenance costs. The intro instead staggers two native TextViews and
  announces the title as one unit; it does not split Chinese characters or loop the effect.
- `PortalTransition`, `Motion` and platform `ViewAnimationUtils` already cover reversible
  containers, one-off icons and fast state changes. Cancellable native property animations
  avoid two frameworks competing for transition ownership.
- Considered but not adopted: AndroidX DynamicAnimation for trackable springs; MotionLayout
  for complex single-screen states; MaterialContainerTransform for Material container morphs;
  Lottie for illustrated branding; GSAP for web text. Current drawn assets/native transitions
  do not justify adding them to an already ~516 MiB model-bearing APK. Rive has not been
  validated with this build toolchain and is **not integrated**.
- The animation gate respects Android `ValueAnimator.areAnimatorsEnabled()` plus an in-app
  **Reduce motion** preference stored locally.

## References and outstanding acceptance

- Android: [Activity shared elements](https://developer.android.com/develop/ui/views/animations/transitions/start-activity),
  [ValueAnimator.areAnimatorsEnabled](https://developer.android.com/reference/android/animation/ValueAnimator),
  [MotionLayout](https://developer.android.com/develop/ui/views/animations/motionlayout).
- Design: [Apple HIG · Motion](https://developer.apple.com/design/human-interface-guidelines/motion),
  [Microsoft Fluent 2 · Motion](https://fluent2.microsoft.design/motion) (continuity, natural response
  and restrained timing), [GSAP SplitText](https://gsap.com/docs/v3/Plugins/SplitText/) (readable titles),
  and the project's internal `ui-animation` skill.
- Static/build checks cover structure and packaging, **not** actual on-device behavior. Still test
  light/dark themes, 200% text, system animation scale 0x, app reduced motion on/off, buttons and
  gesture back, rapid taps, backgrounding, orientation changes and low-end frame times. Until
  measured, do not claim “Apple-level” polish or zero flicker on all routes.
