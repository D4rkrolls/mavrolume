# Physical-device test checklist

Record the device model, Android/One UI version and lens used for each test. Emulators and desktop shader checks do not establish camera compatibility.

- Install and grant Camera access; confirm front/back and available rear lenses, tap-to-focus, flash and EV compensation.
- Open and close the fold, rotate in both directions and switch between cover and inner displays. Confirm the viewfinder and controls remain accessible.
- On the inner display, test the Film, WB, Exposure, Focus, Frame and More pages plus the Film/EV/WB/Focus command dial. Check that the cover stays in its simple layout. The cover display is not used as a second live input display in this build.
- Capture each aspect ratio. Compare preview, processed JPEG and original JPEG for framing, rotation and highlight artifacts.
- Inspect 100, 400 and 3200 Quality settings on a neutral wall, skin, foliage, deep shadows and isolated lamps. Compare grain, bloom and halation at full zoom.
- Check 12 MP and 50 MP choices on every lens. Confirm the selected output dimensions and visible fallback when a resolution is unavailable.
- Test RAW/DNG and manual controls only on lenses that advertise support; check low-storage and capture-error handling.
- Watch preview frame rate and device temperature during extended use, especially on the unfolded display.
