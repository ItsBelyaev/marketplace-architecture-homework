package ru.itsbelyaev.marketplace.service;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.itsbelyaev.marketplace.domain.PromoCodeRow;
import ru.itsbelyaev.marketplace.error.BusinessException;
import ru.itsbelyaev.marketplace.generated.model.DiscountType;
import ru.itsbelyaev.marketplace.generated.model.ErrorCode;
import ru.itsbelyaev.marketplace.generated.model.PromoCodeCreate;
import ru.itsbelyaev.marketplace.generated.model.PromoCodeResponse;
import ru.itsbelyaev.marketplace.repository.PromoCodeRepository;
import ru.itsbelyaev.marketplace.security.AuthenticatedUser;
import ru.itsbelyaev.marketplace.security.CurrentUser;

@Service
public class PromoCodeService {
    private final PromoCodeRepository promoCodes;
    private final CurrentUser currentUser;

    public PromoCodeService(PromoCodeRepository promoCodes, CurrentUser currentUser) {
        this.promoCodes = promoCodes;
        this.currentUser = currentUser;
    }

    public PromoCodeResponse create(PromoCodeCreate request) {
        AuthenticatedUser user = currentUser.get();
        if (!user.hasRole("SELLER") && !user.hasRole("ADMIN")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access denied");
        }
        if (!request.getValidUntil().isAfter(request.getValidFrom())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "valid_until must be after valid_from", Map.of("field", "valid_until"));
        }
        try {
            return toResponse(promoCodes.insert(request));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Promo code already exists", Map.of("field", "code"));
        }
    }

    private PromoCodeResponse toResponse(PromoCodeRow row) {
        return new PromoCodeResponse(
                row.id(), row.code(), DiscountType.fromValue(row.discountType()), row.discountValue(),
                row.minOrderAmount(), row.maxUses(), row.currentUses(), row.validFrom(), row.validUntil(),
                row.active());
    }
}
