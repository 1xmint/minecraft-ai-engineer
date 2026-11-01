package dev.minecraftai.engineer;

/** Contract selection is data validation, never a claim of functional verification. */
final class EngineeringContracts {
    private EngineeringContracts() { }
    static MechanismCatalog.Contract contract(Blueprint blueprint) {
        if(AnalogIntention.describes(blueprint.source())){
            var design=AnalogIntention.validated(blueprint).design();var ports=new java.util.ArrayList<MechanismCatalog.Port>();
            for(var selector:design.selectors())ports.add(new MechanismCatalog.Port("bank-"+selector.bank()+"-"+selector.index(),selector.pos(),true));
            ports.add(new MechanismCatalog.Port("output",design.output(),false));
            return new MechanismCatalog.Contract("comparator-"+design.mode(),ports,java.util.List.of(),8,null,design.mode());
        }
        return GeneratedBlueprint.isGenerated(blueprint)?GeneratedBlueprint.contract(blueprint):MechanismCatalog.contract(blueprint);
    }
}
