<?php
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
return new class extends Migration {
    public function up(): void {
        if (Schema::hasTable('complaints') && !Schema::hasColumn('complaints','admin_note')) {
            Schema::table('complaints', function (Blueprint $t) { $t->text('admin_note')->nullable()->after('status'); });
        }
    }
    public function down(): void {
        if (Schema::hasTable('complaints') && Schema::hasColumn('complaints','admin_note')) {
            Schema::table('complaints', function (Blueprint $t) { $t->dropColumn('admin_note'); });
        }
    }
};
