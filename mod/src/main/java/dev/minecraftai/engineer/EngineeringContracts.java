package dev.minecraftai.engineer;

/** Contract selection is data validation, never a claim of functional verification. */
final class EngineeringContracts {
    private EngineeringContracts() { }
    static MechanismCatalog.Contract contract(Blueprint blueprint) {
        return GeneratedBlueprint.isGenerated(blueprint)?GeneratedBlueprint.contract(blueprint):MechanismCatalog.contract(blueprint);
    }
}
