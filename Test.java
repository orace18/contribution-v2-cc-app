public class Test {
    private static final String PROVISIONAL_COUNTRY_CODE = "INT";
    public static void main(String[] args) {
        String currentCountryCode = "INT";
        String selectedCountryCode = "BJ";
        boolean immutable = isImmutableCountrySelection(currentCountryCode, selectedCountryCode);
        System.out.println("immutable=" + immutable);
    }
    private static boolean isImmutableCountrySelection(String currentCountryCode, String selectedCountryCode) {
        if (currentCountryCode == null || currentCountryCode.equals(selectedCountryCode)) {
            return false;
        }
        return !PROVISIONAL_COUNTRY_CODE.equals(currentCountryCode);
    }
}
