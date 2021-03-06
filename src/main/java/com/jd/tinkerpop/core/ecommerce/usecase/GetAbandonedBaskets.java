package com.jd.tinkerpop.core.ecommerce.usecase;

import com.jd.tinkerpop.core.ecommerce.domain.BasketDetail;
import com.jd.tinkerpop.core.ecommerce.domain.OrderLine;
import com.jd.tinkerpop.core.ecommerce.repository.UserGraphRepository;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import com.tinkerpop.pipes.util.structures.Pair;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.tinkerpop.blueprints.Direction.IN;

@Component
public class GetAbandonedBaskets {

    private final UserGraphRepository userGraphRepository;

    @Autowired
    public GetAbandonedBaskets(UserGraphRepository userGraphRepository) {
        this.userGraphRepository = userGraphRepository;
    }

    public BasketDetail getAbandonedBaskets(String email) {
        Vertex vCustomer = userGraphRepository.getForUser(email);
        return toBaskets(vCustomer);
    }

    private BasketDetail toBaskets(final Vertex vCustomer) {
        BasketDetail basketDetail = new BasketDetail();
        basketDetail.setEmail(vCustomer.getProperty("email"));
        List<OrderLine> lines = new ArrayList<>();
        DateTime curCheckOut = getCurrentCheckout(vCustomer);

        GremlinPipeline pipe = new GremlinPipeline();
        pipe.start(vCustomer);
        List<Vertex> vProducts = pipe.outE("visit").inV().outE("add to basket").as("basket").property("createdAt")
                .filter((argument) -> ((DateTime) argument).isAfter(curCheckOut)).back("basket").inV().toList();
        for (Vertex vProduct : vProducts) {
            OrderLine orderLine = new OrderLine();
            orderLine.setName(vProduct.getProperty("name"));
            orderLine.setImage(vProduct.getProperty("image"));
            orderLine.setPrice(vProduct.getProperty("price"));
            orderLine.setQty(getQty(vProduct, curCheckOut));
            lines.add(orderLine);
        }
        basketDetail.setBasket(lines);
        return basketDetail;
    }

    private DateTime getCurrentCheckout(final Vertex vCustomer) {
        GremlinPipeline pipe = new GremlinPipeline();
        pipe.start(vCustomer);
        return (DateTime) pipe.outE("visit").inV().outE("check out").order((argument) ->
                ((DateTime) ((Pair<Edge, Edge>) argument).getB().getProperty("createdAt"))
                        .compareTo(((Pair<Edge, Edge>) argument).getA().getProperty("createdAt"))
        ).property("createdAt").toList().get(0);
    }

    private int getQty(Vertex product, DateTime curCheckOut) {
        List<Edge> eAddToBaskets = (List<Edge>) product.getEdges(IN, "add to basket");
        int qty = 0;
        for (Edge eAdd : eAddToBaskets) {
            DateTime addToBasketTime = eAdd.getProperty("createdAt");
            if (addToBasketTime.isAfter(curCheckOut)) {
                qty += (int) eAdd.getProperty("qty");
            }
        }
        return qty;
    }
}
