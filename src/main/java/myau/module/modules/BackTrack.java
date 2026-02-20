// Replace property declarations:
public final BooleanSetting  legit        = new BooleanSetting("Legit",          false);
public final BooleanSetting  releaseOnHit = new BooleanSetting("Release On Hit", true);
public final SliderSetting   delay        = new SliderSetting("Delay",           400,   0, 1000, 10);
public final SliderSetting   hitRange     = new SliderSetting("Range",           3.0, 3.0,  10.0, 0.1);
public final BooleanSetting  onlyIfNeeded = new BooleanSetting("Only If Needed", true);
public final BooleanSetting  esp          = new BooleanSetting("ESP",            true);
public final DropdownSetting espMode      = new DropdownSetting("ESP Mode",      0, "Hitbox", "None");

// Replace constructor:
public BackTrack() {
    super("BackTrack", false);
    register(legit);
    register(releaseOnHit);
    register(delay);
    register(hitRange);
    register(onlyIfNeeded);
    register(esp);
    register(espMode);
}

// Update getValue() calls in logic:
// timer.hasTimePassed(delay.getValue()) → timer.hasTimePassed((int) delay.getValue())
// hitRange.getValue()                   → (float) hitRange.getValue()  or  hitRange.getValue() (double works too)
// espMode.getValue()                    → espMode.getIndex()
// if (espMode.getValue() != 0)          → if (espMode.getIndex() != 0)
// legit.getValue()                      → legit.getValue()       (unchanged)
// releaseOnHit.getValue()               → releaseOnHit.getValue() (unchanged)
// onlyIfNeeded.getValue()               → onlyIfNeeded.getValue() (unchanged)
// esp.getValue()                        → esp.getValue()          (unchanged)
