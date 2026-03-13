// Initialize the paymentservice database and collection
db = db.getSiblingDB('paymentservice');
db.createCollection('payment_attempts');
