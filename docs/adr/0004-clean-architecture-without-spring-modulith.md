# Clean architecture with a plain-Java domain, not Spring Modulith

Every module has the same shape: `domain` (`model`, `ports/in`, `ports/out`, `service`) and `infrastructure` (`adapters/in`, `adapters/out`, `configuration`). `domain` is plain Java with no Spring annotations, and its services are registered as beans in `infrastructure/configuration`. Modules reach each other only through `domain/ports/in` or the `shared` module. An ArchUnit test enforces these rules, because the use cases and ports are already the boundary and we don't want a framework's module model on top of them.

The Farmers Market is not a module. It is an external system used only by Food Storage, so it is an outbound port in Food Storage with its client as an adapter there.

## Considered Options

- **Spring Modulith**: verifies module boundaries out of the box, but treats sub-packages as internal, so our nested `domain/ports/in` would need named-interface declarations. It also rejects cycles between modules, which would forbid Food Storage telling the Kitchen an Order is ready through the Kitchen's inbound port.
- **Spring annotations on domain services**: less wiring code, but the domain would depend on the framework.
- **A separate `market` module**: it would hold no domain of its own, only a client.

## Consequences

The Kitchen and Food Storage may call each other's inbound ports in both directions, so nothing checks for cycles between modules. Splitting them into services later would need one of those directions to become an event.
