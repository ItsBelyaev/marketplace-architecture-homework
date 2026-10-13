package ru.itsbelyaev.marketplace.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.itsbelyaev.marketplace.generated.api.PromoCodesApiDelegate;
import ru.itsbelyaev.marketplace.generated.model.PromoCodeCreate;
import ru.itsbelyaev.marketplace.generated.model.PromoCodeResponse;
import ru.itsbelyaev.marketplace.service.PromoCodeService;

@Service
public class PromoCodesApiDelegateImpl implements PromoCodesApiDelegate {
    private final PromoCodeService promoCodeService;

    public PromoCodesApiDelegateImpl(PromoCodeService promoCodeService) {
        this.promoCodeService = promoCodeService;
    }

    @Override
    public ResponseEntity<PromoCodeResponse> createPromoCode(PromoCodeCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promoCodeService.create(request));
    }
}
