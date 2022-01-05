var bened;
(function (bened) {
    var SoftMG = (function () {
        function SoftMG() { }
        
	SoftMG.prototype.calc = function (balance, amount, last, genesisBalance, forgePercent) {
            var payout = this.ordinary(balance, amount, last, Math.abs(genesisBalance), forgePercent);

            if (payout > 10000000000)  payout = 10000000000;

            if (payout + balance > 100000000000) payout = 100000000000 - balance;

	    if (payout < 0)  payout = 0;
            return (payout / 1000000.0).toFixed(6);
        };
        SoftMG.prototype.ordinary = function (balance_in, amount_in, last, genesisBalance, forgePercent) {
            var balance = balance_in;
            var amount = amount_in;
            var multi = this.multi(balance, amount, forgePercent, genesisBalance);

            var days = this.days(last);
            var payout = balance * (days * multi);

            return payout;
        };
        SoftMG.prototype.days = function (last) {
            var seconds = this.seconds(last);
            return seconds / 86400.0;
            
        };
        SoftMG.prototype.seconds = function (last) {
            var time = Date.now();
            time = time / 1000.0;
            var diff = last;
            diff = diff + 1639874401001/1000.0;  //это время запуска было 53271548E9
            return time - diff;
        };
        SoftMG.prototype.multi = function (balance, amount, forgePercent, genesisBalance) {
            var multi = 1.0;
            var percent = 0.0;
            var stpKof = 1.0;
            
            if (balance >= 1 && balance <= 999999)
                percent = 1;
            if (balance >= 1000000 && balance <= 9999999999)
                percent = 0.25;
            if (balance >= 10000000000 && balance <= 99999999999)
                percent = 0.5;
            if(percent>0 && forgePercent>0)percent=1;

            if (amount >= 100000000000 && amount <= 999999999999)
                multi = 1.2;
            if (amount >= 1000000000000 && amount <= 9999999999999)
                multi = 1.5;
            if (amount >= 10000000000000 && amount <= 99999999999999)
                multi = 1.8;
            if (amount >= 100000000000000)
                multi = 2.0;
            
        if (genesisBalance>=30000000000000000    && genesisBalance<=44999999999999999) stpKof = 0.85;  // от 30 do 45 
        if (genesisBalance>=45000000000000000    && genesisBalance<=59999999999999999) stpKof = 0.7; // от 45    до    60
        if (genesisBalance>=60000000000000000    && genesisBalance<=74999999999999999) stpKof = 0.55; // 60    до    75
        if (genesisBalance>=75000000000000000    && genesisBalance<=89999999999999999) stpKof = 0.4; // 75    до   90
        if (genesisBalance>=90000000000000000    && genesisBalance<=99999999999999999) stpKof = 0.25; // 90    до   100
        if (genesisBalance>=100000000000000000) stpKof = 0.15;  
            return (multi * percent * stpKof) / 100.0;
        };
        return SoftMG;
    }());
    bened.SoftMG = SoftMG;
    SoftMG["__class"] = "bened.SoftMG";
})(bened || (bened = {}));

