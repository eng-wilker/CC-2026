"use strict";

const terms = ["cc26part4data", "astronomy", "photography", "cycling", "cooking", "music"];
let next = 0;

module.exports = {
  selectSearchTerm(context, events, done) {
    context.vars.searchTerm = terms[next++ % terms.length];
    return done();
  },
};
