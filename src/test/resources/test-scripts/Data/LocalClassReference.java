public class LocalClassReference {

   public LocalClassReference ref = null;

   public static void main(String[] args) {

      LocalClassReference one = new LocalClassReference();
      one.ref = one;
   }

}
