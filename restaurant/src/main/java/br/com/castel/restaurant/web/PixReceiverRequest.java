package br.com.castel.restaurant.web;

/** Body of {@code PUT /api/admin/pix-receiver}; every rule is the domain's ({@code INVALID_PIX_RECEIVER}). */
public class PixReceiverRequest {

    private String key;

    private String receiverName;

    private String city;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
